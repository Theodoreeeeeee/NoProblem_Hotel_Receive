package cn.itcast.hotel;

import cn.itcast.hotel.service.impl.HotelService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class HotelSearchTest {

    @Autowired
    private HotelService hotelService;

    @Autowired
    private RestHighLevelClient client;

//    @Test
//    void testContextLoad() {
//        Map<String, List<String>> filters = hotelService.filters();
//        System.out.println("filters = " + filters);
//    }

    @Test
    void testSuggest() throws IOException {
        SearchRequest request = new SearchRequest("hotel");
        request.source().suggest(new SuggestBuilder()
                .addSuggestion(
                        "suggestions", SuggestBuilders
                                .completionSuggestion("suggestion")
                                .prefix("hs")
                                .skipDuplicates(true)
                                .size(10)
                ));
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        Suggest suggest = response.getSuggest();
//        Suggest.Suggestion<? extends Suggest.Suggestion.Entry<? extends Suggest.Suggestion.Entry.Option>> suggestion = suggest.getSuggestion("suggestion");
        CompletionSuggestion suggestion = suggest.getSuggestion("suggestions");
        for (CompletionSuggestion.Entry.Option option : suggestion.getOptions()) {
            String text = option.getText().string();
            System.out.println("text = " + text);
        }
//        System.out.println("response = " + response);
    }

}
