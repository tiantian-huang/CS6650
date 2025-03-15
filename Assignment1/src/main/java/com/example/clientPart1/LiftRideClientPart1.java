package com.example.clientPart1;

import com.example.model.LiftRide;
import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.util.Random;
import java.util.concurrent.*;

public class LiftRideClientPart1 {
  private static final int TOTAL_REQUESTS = 200000;
  private static final int INITIAL_THREADS = 32;
  private static final int REQUESTS_PER_THREAD = 1000;

  private static final String SERVER_URL = "http://52.33.137.135:8080/JavaServlets_war/skiers/9/seasons/2025/day/1/skier/20";
  private static final AtomicInteger successfulRequests = new AtomicInteger(0);
  private static final AtomicInteger failedRequests = new AtomicInteger(0);

  public static void main(String[] args) throws InterruptedException {
    // Changed to newCachedThreadPool to handle dynamic thread creation
    ExecutorService executor = Executors.newCachedThreadPool();
    BlockingQueue<LiftRide> queue = new LinkedBlockingQueue<>();

    /** Record start time */
    long startTime = System.currentTimeMillis();

    /** Thread for generating random LiftRide events */
    new Thread(() -> {
      Random random = new Random();
      for (int i = 0; i < TOTAL_REQUESTS; i++) {
        LiftRide liftRide = new LiftRide(
            random.nextInt(100000) + 1, // skierID: 1-100000
            random.nextInt(10) + 1,     // resortID: 1-10
            random.nextInt(40) + 1,     // liftID: 1-40
            2025,                       // seasonID: 2025
            1,                          // dayID: 1
            random.nextInt(360) + 1     // time: 1-360
        );
        queue.add(liftRide);
      }
    }).start();

    /** Initial phase: submit initial threads */
    for (int i = 0; i < INITIAL_THREADS; i++) {
      executor.submit(() -> {
        for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
          processRequest(queue);
        }
      });
    }

    /** Calculate and submit remaining tasks */
    int remainingRequests = TOTAL_REQUESTS - (INITIAL_THREADS * REQUESTS_PER_THREAD);
    int remainingTasks = remainingRequests / REQUESTS_PER_THREAD;

    for (int i = 0; i < remainingTasks; i++) {
      executor.submit(() -> {
        for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
          processRequest(queue);
        }
      });
    }

    // Handle any remaining requests that don't fill a complete batch
    int finalRemainingRequests = remainingRequests % REQUESTS_PER_THREAD;
    if (finalRemainingRequests > 0) {
      executor.submit(() -> {
        for (int j = 0; j < finalRemainingRequests; j++) {
          processRequest(queue);
        }
      });
    }

    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.HOURS);

    /** Record end time */
    long endTime = System.currentTimeMillis();
    long totalRunTime = endTime - startTime;
    double throughput = (double) successfulRequests.get() / (totalRunTime / 1000.0);

    /** Print results */
//    System.out.println("Successful requests: " + 200000);
//    System.out.println("Failed requests: " + 0);
//    System.out.println("Total run time: " + 522579 + " ms");
//    System.out.println("Total throughput: " + 398.4137287881564 + " requests/sec");

    System.out.println("Successful requests: " + successfulRequests.get());
    System.out.println("Failed requests: " + failedRequests.get());
    System.out.println("Total run time: " + totalRunTime + " ms");
    System.out.println("Total throughput: " + throughput + " requests/sec");
  }

  private static void processRequest(BlockingQueue<LiftRide> queue) {
    try {
      LiftRide liftRide = queue.take();
      int statusCode = sendRequest(liftRide);

      if (statusCode == 201) {
        successfulRequests.incrementAndGet();
      } else {
        failedRequests.incrementAndGet();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static int sendRequest(LiftRide liftRide) {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost(SERVER_URL);
      httpPost.setHeader("Content-Type", "application/json");

      Gson gson = new Gson();
      String json = gson.toJson(liftRide);
      httpPost.setEntity(new StringEntity(json));

      try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
        return response.getCode();
      }
    } catch (Exception e) {
      return -1;
    }
  }
}