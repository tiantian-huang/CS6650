package com.example.servlet;

import com.example.model.LiftRide;
import com.google.gson.Gson;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@WebServlet("/skiers/*")
public class SkiersServlet extends HttpServlet {

    private Connection rabbitMQConnection;
    private final String QUEUE_NAME = "liftRideQueue";

    @Override
    public void init() throws ServletException {
        ConnectionFactory factory = new ConnectionFactory();
        // RabbitMQ server IP
        factory.setHost("35.161.247.84");
        factory.setPort(5672);
         factory.setUsername("htt");
         factory.setPassword("940430");
        try {
            rabbitMQConnection = factory.newConnection();
        } catch (Exception e) {
            throw new ServletException("Failed to create RabbitMQ connection", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String urlPath = req.getPathInfo();
        if (urlPath == null || urlPath.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("Missing URL parameters");
            return;
        }
        // expected URL: /{resortID}/seasons/{seasonID}/day/{dayID}/skier/{skierID}
        String[] urlParts = urlPath.split("/");
        if (urlParts.length != 8) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("Invalid URL format");
            return;
        }
        try {
            int resortID = Integer.parseInt(urlParts[1]);
            int seasonID = Integer.parseInt(urlParts[3]);
            int dayID = Integer.parseInt(urlParts[5]);
            int urlSkierID = Integer.parseInt(urlParts[7]);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("URL parameters must be numbers");
            return;
        }

        Gson gson = new Gson();
        LiftRide liftRide = gson.fromJson(req.getReader(), LiftRide.class);

        if (liftRide.getSkierID() < 1 || liftRide.getSkierID() > 100000) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("Invalid skierID");
            return;
        }
        if (liftRide.getResortID() < 1 || liftRide.getResortID() > 10) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("Invalid resortID");
            return;
        }
        if (liftRide.getLiftID() < 1 || liftRide.getLiftID() > 40) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("Invalid liftID");
            return;
        }
        if (liftRide.getTime() < 1 || liftRide.getTime() > 360) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("Invalid time");
            return;
        }

        try (Channel channel = rabbitMQConnection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            String message = gson.toJson(liftRide);
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("Failed to enqueue message");
            return;
        }

        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().write("Lift ride recorded");
    }

    @Override
    public void destroy() {
        try {
            if (rabbitMQConnection != null) {
                rabbitMQConnection.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write("Welcome to the Skiers API! Use POST to record a lift ride.");
    }
}
