package com.example.servlet;

import com.example.model.LiftRide;
import com.google.gson.Gson;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;

@WebServlet("/skiers")
public class SkiersServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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

        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().write("Lift ride recorded");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write("Welcome to the Skiers API! Use POST to record a lift ride.");
    }
}