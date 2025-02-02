package com.gvgroup.ordermanagement.facade;


import com.gvgroup.ordermanagement.config.HazelcastConfig;
import com.gvgroup.ordermanagement.entity.Order;
import com.gvgroup.ordermanagement.model.message.OrderCreatedMessage;
import com.gvgroup.ordermanagement.model.message.OrderDeletedMessage;
import com.gvgroup.ordermanagement.model.request.CreateOrderRequest;
import com.gvgroup.ordermanagement.model.request.UpdateOrderRequest;
import com.gvgroup.ordermanagement.model.response.OrderDetailsResponse;
import com.gvgroup.ordermanagement.model.response.PageableOrderDetailsResponse;
import com.gvgroup.ordermanagement.security.service.OrderQueryService;
import com.gvgroup.ordermanagement.service.MessagePublisherService;
import com.gvgroup.ordermanagement.service.OrderService;
import com.gvgroup.ordermanagement.value.OrderId;
import com.gvgroup.ordermanagement.value.UserId;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.gvgroup.ordermanagement.utils.KafkaConstants.ORDER_CREATED_TOPIC_NAME;
import static com.gvgroup.ordermanagement.utils.KafkaConstants.ORDER_DELETED_TOPIC_NAME;

@Component
public class OrderFacade {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private final IMap<OrderId, Order> orderCache;
    private final MessagePublisherService messagePublisherService;

    public OrderFacade(OrderService orderService, OrderQueryService orderQueryService, HazelcastInstance hazelcastInstance, MessagePublisherService messagePublisherService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
        this.orderCache = hazelcastInstance.getMap(HazelcastConfig.ORDERS);
        this.messagePublisherService = messagePublisherService;
    }

    public ResponseEntity<OrderDetailsResponse> createOrder(UserId userId, CreateOrderRequest orderRequest) {
        OrderId orderId = OrderId.create();
        Order order = orderService.createOrder(orderId,
                userId,
                orderRequest.getProduct(),
                orderRequest.getQuantity(),
                orderRequest.getPrice());
        orderCache.put(orderId, order);
        messagePublisherService.publish(ORDER_CREATED_TOPIC_NAME, OrderCreatedMessage.convert(order));
        return new ResponseEntity<>(OrderDetailsResponse.toJson(order), HttpStatus.CREATED);
    }

    public ResponseEntity<OrderDetailsResponse> getOrderDetails(OrderId orderId) {
        Order order = orderCache.computeIfAbsent(orderId, id ->  orderQueryService.findOrderByOrderId(id));
        return new ResponseEntity<>(OrderDetailsResponse.toJson(order), HttpStatus.OK);
    }

    public ResponseEntity<PageableOrderDetailsResponse> getOrders(UserId userId, int page, int size) {
        Page<Order> orders = orderQueryService.findAllOrdersByUserId(userId, page, size);
        return new ResponseEntity<>(PageableOrderDetailsResponse.toJson(orders), HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<OrderDetailsResponse> updateOrder(OrderId orderId, UpdateOrderRequest updateOrderRequest) {
        Order order = orderService.updateOrder(orderId, updateOrderRequest.getProduct(), updateOrderRequest.getPrice(), updateOrderRequest.getQuantity());
        orderCache.put(orderId, order);
        return new ResponseEntity<>(OrderDetailsResponse.toJson(order), HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<Void> deleteOrder(OrderId orderId) {
        orderService.deleteOrder(orderId);
        orderCache.remove(orderId);
        messagePublisherService.publish(ORDER_DELETED_TOPIC_NAME, OrderDeletedMessage.convert(orderId));
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
