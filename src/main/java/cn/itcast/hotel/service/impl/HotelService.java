package cn.itcast.hotel.service.impl;

import cn.itcast.hotel.mapper.HotelMapper;
import cn.itcast.hotel.pojo.Hotel;
import cn.itcast.hotel.pojo.HotelDoc;
import cn.itcast.hotel.pojo.PageResult;
import cn.itcast.hotel.pojo.RequestParams;
import cn.itcast.hotel.service.IHotelService;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HotelService extends ServiceImpl<HotelMapper, Hotel> implements IHotelService {

    //    @Autowired
    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Override
    public PageResult search(RequestParams params) {
        try {
            SearchRequest searchRequest = new SearchRequest("hotel");
            buildBasicQuery(params, searchRequest);
            int page = params.getPage(), size = params.getSize();
            searchRequest.source().from((page - 1) * size).size(size);
            String location = params.getLocation();
            if (location != null && !"".equals(location)) {
                searchRequest.source().sort(SortBuilders.
                        geoDistanceSort("location", new GeoPoint(location))
                        .order(SortOrder.ASC)
                        .unit(DistanceUnit.KILOMETERS));
            }
            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            return handleResponse(response);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    @Override
    public Map<String, List<String>> filters(RequestParams params) {
        try {
            SearchRequest request = new SearchRequest("hotel");
            // 先query，限定聚合的过滤条件
            buildBasicQuery(params, request);
            request.source().size(0);
            // 聚合构建
            buildAggregation(request);
            Map<String, List<String>> result = new HashMap<>();
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            Aggregations aggregations = response.getAggregations();
            List<String> brands = getBrands(aggregations, "brandAgg");
            result.put("品牌", brands);
            List<String> cities = getBrands(aggregations, "cityAgg");
            result.put("城市", cities);
            List<String> stars = getBrands(aggregations, "starAgg");
            result.put("星级", stars);
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> getSuggestion(String key) {
        try {
            SearchRequest request = new SearchRequest("hotel");
            request.source().suggest(new SuggestBuilder()
                    .addSuggestion("suggestions", SuggestBuilders.completionSuggestion("suggestion")
                            .prefix(key)
                            .skipDuplicates(true)
                            .size(10)));
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            CompletionSuggestion suggestion = response.getSuggest().getSuggestion("suggestions");
            List<String> suggestions = new ArrayList<>();
            for (CompletionSuggestion.Entry.Option option : suggestion.getOptions()) {
                suggestions.add(option.getText().toString());
            }
            return suggestions;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getBrands(Aggregations aggregations, String aggName) {
        Terms brandAgg = aggregations.get(aggName);
        List<? extends Terms.Bucket> buckets = brandAgg.getBuckets();
        List<String> brands = new ArrayList<>();
        for (Terms.Bucket bucket : buckets) {
            String key = bucket.getKeyAsString();
            brands.add(key);
        }
        return brands;
    }

    private void buildAggregation(SearchRequest request) {
        request.source().aggregation(AggregationBuilders
                .terms("brandAgg")
                .field("brand")
                .size(100));
        request.source().aggregation(AggregationBuilders
                .terms("cityAgg")
                .field("city")
                .size(100));
        request.source().aggregation(AggregationBuilders
                .terms("starAgg")
                .field("starName")
                .size(100));
    }

    private void buildBasicQuery(RequestParams params, SearchRequest searchRequest) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        String key = params.getKey();
        if (key == null || "".equals(key)) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        } else {
            boolQuery.must(QueryBuilders.matchQuery("all", key));
        }
        if (params.getCity() != null && !"".equals(params.getCity())) {
            boolQuery.filter(QueryBuilders.termQuery("city", params.getCity()));
        }
        if (params.getBrand() != null && !"".equals(params.getBrand())) {
            boolQuery.filter(QueryBuilders.termQuery("brand", params.getBrand()));
        }
        if (params.getStarName() != null && !"".equals(params.getStarName())) {
            boolQuery.filter(QueryBuilders.termQuery("starName", params.getStarName()));
        }
        if (params.getMaxPrice() != null && params.getMinPrice() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("price").gte(params.getMinPrice()).lte(params.getMaxPrice()));
        }

        // function_score
        FunctionScoreQueryBuilder functionScoreQuery =
                QueryBuilders.functionScoreQuery(
                        // 原始查询，相关性算法查询
                        boolQuery,
                        // function score 数组
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                                // 其中的一个function score元素
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                        // 过滤条件
                                        QueryBuilders.termQuery("isAD", true),
                                        // 算分逻辑
                                        ScoreFunctionBuilders.weightFactorFunction(10)
                                )
                        });
        searchRequest.source().query(functionScoreQuery);
    }


    private PageResult handleResponse(SearchResponse response) {
        SearchHits searchHits = response.getHits();
        long total = searchHits.getTotalHits().value;
        System.out.println("共搜索到条" + total + "数据");
        SearchHit[] hits = searchHits.getHits();
        List<HotelDoc> hotels = new ArrayList<>();
        for (SearchHit hit : hits) {
            String json = hit.getSourceAsString();
            HotelDoc hotelDoc = JSON.parseObject(json, HotelDoc.class);
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if (!CollectionUtils.isEmpty(highlightFields)) {
                HighlightField highlightField = highlightFields.get("name");
                if (highlightField != null) {
                    String name = highlightField.getFragments()[0].toString();
                    hotelDoc.setName(name);
                }
            }
//            System.out.println("hotelDoc = " + hotelDoc);
            Object[] sortValues = hit.getSortValues();
            if (sortValues.length > 0) {
                Object sortValue = sortValues[0];
                hotelDoc.setDistance(sortValue);
            }
            hotels.add(hotelDoc);
        }
        return new PageResult(total, hotels);
    }
}
