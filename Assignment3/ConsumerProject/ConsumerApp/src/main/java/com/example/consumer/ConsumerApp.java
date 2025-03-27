package com.example.consumer;

import com.example.model.LiftRide;
import com.google.gson.Gson;
import com.rabbitmq.client.*;
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class ConsumerApp {

  private static final String QUEUE_NAME = "liftRideQueue";
  private static final String RABBITMQ_HOST = "35.87.41.40";
  private static final String USERNAME = "htt";
  private static final String PASSWORD = "940430";
  private static final int THREAD_COUNT = 20;

  private static ConcurrentHashMap<Integer, Integer> skierRideCount = new ConcurrentHashMap<>();

  public static void main(String[] args) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(RABBITMQ_HOST);
    factory.setUsername(USERNAME);
    factory.setPassword(PASSWORD);
    Connection connection = factory.newConnection();

    ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
    for (int i = 0; i < THREAD_COUNT; i++) {
      executorService.submit(new ConsumerTask(connection));
    }
  }

  static class ConsumerTask implements Runnable {
    private final Connection connection;
    private final Gson gson = new Gson();
    private Jedis jedis;

    ConsumerTask(Connection connection) {
      this.connection = connection;
      this.jedis = new Jedis("localhost", 6379);  // Redis 6379
    }

    @Override
    public void run() {
      try {
        Channel channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        channel.basicQos(10);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
          try {
            LiftRide liftRide = gson.fromJson(message, LiftRide.class);

            String dayKey = liftRide.getSeasonID() + "-" + liftRide.getDayID();
            String skierDaysKey = "skier:" + liftRide.getSkierID() + ":days";
            String skierVerticalKey = "skier:" + liftRide.getSkierID() + ":vertical";
            String skierLiftsKey = "skier:" + liftRide.getSkierID() + ":day:" + dayKey + ":lifts";
            String resortSkiersKey = "resort:" + liftRide.getResortID() + ":day:" + dayKey + ":skiers";

            jedis.sadd(skierDaysKey, dayKey);
            jedis.hincrBy(skierVerticalKey, dayKey, liftRide.getLiftID() * 10);
            jedis.sadd(skierLiftsKey, String.valueOf(liftRide.getLiftID()));
            jedis.sadd(resortSkiersKey, String.valueOf(liftRide.getSkierID()));

            skierRideCount.merge(liftRide.getSkierID(), 1, Integer::sum);

            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
          } catch (Exception e) {
            e.printStackTrace();
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
          }
        };
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
