package cn.itcast.hotel.mq;

import cn.itcast.hotel.constants.MqConstants;
import cn.itcast.hotel.service.IHotelService;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static cn.itcast.hotel.constants.MqConstants.*;

@Component
public class HotelListener {
    @Autowired
    private IHotelService hotelService;

    /**
     * 监听酒店新增或修改
     *
     * @param id
     */
//    @RabbitListener(queues = HOTEL_INSERT_QUEUE)
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = HOTEL_INSERT_QUEUE),
            exchange = @Exchange(name = HOTEL_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = HOTEL_INSERT_KEY
    ))
    public void listenHotelInsertOrUpdate(Long id) {
        System.err.println("INSERTTTTTTTTTTTTT");
        hotelService.insertById(id);
    }

    /**
     * 监听酒店删除
     *
     * @param id
     */
//    @RabbitListener(queues = HOTEL_DELETE_QUEUE)
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = HOTEL_DELETE_QUEUE),
            exchange = @Exchange(name = HOTEL_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = HOTEL_DELETE_KEY
    ))

    public void listenHotelDelete(Long id) {
        System.err.println("DELETEEEEEEEEEEEEEEEEEEEEEEEEEE");
        hotelService.deleteById(id);
    }
}
