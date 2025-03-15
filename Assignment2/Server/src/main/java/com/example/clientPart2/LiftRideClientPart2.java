package com.example.clientPart2;

import com.example.model.LiftRide;
import com.google.gson.Gson;
import java.io.PrintWriter;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LiftRideClientPart2 {
  private static final int TOTAL_REQUESTS = 200000;
  private static final int INITIAL_THREADS = 32;
  private static final int REQUESTS_PER_THREAD = 1000;
  private static final String SERVER_URL = "http://assignment2-alb-1450136127.us-west-2.elb.amazonaws.com/Assignment2-1.0-SNAPSHOT/skiers/9/seasons/2025/day/1/skier/20";
//  for single servlet:
//  private static final String SERVER_URL = "http://35.90.14.101:8080/Assignment2-1.0-SNAPSHOT/skiers/9/seasons/2025/day/1/skier/20";

  private static final AtomicInteger successfulRequests = new AtomicInteger(0);
  private static final AtomicInteger failedRequests = new AtomicInteger(0);
  private static final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

  public static void main(String[] args) throws InterruptedException, IOException {
    // Use a cached thread pool to dynamically create threads
    ExecutorService executor = Executors.newCachedThreadPool();
    BlockingQueue<LiftRide> queue = new LinkedBlockingQueue<>();
    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter("latency.csv"));
    PrintWriter csvWriter = new PrintWriter(bufferedWriter);
    csvWriter.println("StartTime,RequestType,Latency,ResponseCode");

    /** Record start time */
    long startTime = System.currentTimeMillis();

    /** Thread for generating requests */
    new Thread(() -> {
      Random random = new Random();
      for (int i = 0; i < TOTAL_REQUESTS; i++) {
        LiftRide liftRide = new LiftRide(
            random.nextInt(100000) + 1,
            random.nextInt(10) + 1,
            random.nextInt(40) + 1,
            2025,
            1,
            random.nextInt(360) + 1
        );
        queue.add(liftRide);
      }
    }).start();

    /** Initial phase: submit 32 threads, each processing 1000 requests */
    for (int i = 0; i < INITIAL_THREADS; i++) {
      executor.submit(() -> {
        for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
          processRequest(queue, csvWriter);
        }
      });
    }

    /** Calculate and submit remaining tasks */
    int remainingRequests = TOTAL_REQUESTS - (INITIAL_THREADS * REQUESTS_PER_THREAD);
    int remainingTasks = remainingRequests / REQUESTS_PER_THREAD;

    for (int i = 0; i < remainingTasks; i++) {
      executor.submit(() -> {
        for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
          processRequest(queue, csvWriter);
        }
      });
    }

    /** Shutdown thread pool and wait for all tasks to complete */
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.HOURS);
    csvWriter.close();

    /** Calculate and output results */
    long endTime = System.currentTimeMillis();
    long totalRunTime = endTime - startTime;
    double throughput = (double) successfulRequests.get() / (totalRunTime / 1000.0);

    /** Calculate latency metrics */
    Collections.sort(latencies);
    long totalLatency = latencies.stream().mapToLong(Long::longValue).sum();
    double meanLatency = (double) totalLatency / latencies.size();
    double medianLatency = latencies.get(latencies.size() / 2);
    long p99Latency = latencies.get((int) (latencies.size() * 0.99));
    long maxLatency = latencies.get(latencies.size() - 1);
    long minLatency = latencies.get(0);

    /** Output results */
    System.out.println("Successful requests: " + successfulRequests.get());
    System.out.println("Failed requests: " + failedRequests.get());
    System.out.println("Total run time: " + totalRunTime + " ms");
    System.out.println("Total throughput: " + throughput + " requests/sec");
    System.out.println("Mean latency: " + meanLatency + " ms");
    System.out.println("Median latency: " + medianLatency + " ms");
    System.out.println("p99 latency: " + p99Latency + " ms");
    System.out.println("Max latency: " + maxLatency + " ms");
    System.out.println("Min latency: " + minLatency + " ms");
  }

  private static void processRequest(BlockingQueue<LiftRide> queue, PrintWriter csvWriter) {
    try {
      LiftRide liftRide = queue.take();
      long requestStartTime = System.currentTimeMillis();
      int statusCode = sendRequest(liftRide);
      long requestEndTime = System.currentTimeMillis();
      long latency = requestEndTime - requestStartTime;

      synchronized (latencies) {
        latencies.add(latency);
      }

      if (statusCode == 201) {
        successfulRequests.incrementAndGet();
      } else {
        failedRequests.incrementAndGet();
      }

      synchronized (csvWriter) {
        csvWriter.println(requestStartTime + ",POST," + latency + "," + statusCode);
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