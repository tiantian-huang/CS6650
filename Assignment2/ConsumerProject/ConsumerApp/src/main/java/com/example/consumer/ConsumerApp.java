package com.example.consumer;

import com.example.model.LiftRide;
import com.google.gson.Gson;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class ConsumerApp {

  private static final String QUEUE_NAME = "liftRideQueue";
  // RabbitMQ server IP
  private static final String RABBITMQ_HOST = "35.161.247.84";
  private static final String USERNAME = "htt";
  private static final String PASSWORD = "940430";

  private static final int THREAD_COUNT = 20;

  private static ConcurrentHashMap<Integer, Integer> skierRideCount = new ConcurrentHashMap<>();

  public static void main(String[] args) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(RABBITMQ_HOST);
    factory.setPort(5672);
    factory.setUsername(USERNAME);
    factory.setPassword(PASSWORD);

    Connection connection = factory.newConnection();

    ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    for (int i = 0; i < THREAD_COUNT; i++) {
      executorService.submit(new ConsumerTask(connection));
    }

    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.scheduleAtFixedRate(() -> {
      System.out.println("The records for every skier：");
      skierRideCount.forEach((skierId, count) -> System.out.println("Skier " + skierId + ": " + count));
    }, 1, 10, TimeUnit.SECONDS);
  }

  static class ConsumerTask implements Runnable {
    private final Connection connection;
    private final Gson gson = new Gson();

    ConsumerTask(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void run() {
      try {
        Channel channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        channel.basicQos(1);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
          try {
            LiftRide liftRide = gson.fromJson(message, LiftRide.class);
            skierRideCount.merge(liftRide.getSkierID(), 1, Integer::sum);
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
          } catch (Exception e) {
            e.printStackTrace();
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
          }
        };
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
