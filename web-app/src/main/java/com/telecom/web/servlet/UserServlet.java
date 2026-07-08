package com.telecom.web.servlet;

import com.telecom.web.dao.UserDAO;
import com.telecom.web.model.User;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * Handles CRUD operations for the USERS table.
 *
 * Routes:
 *  GET  /users            -> list all users          (users.jsp)
 *  GET  /users?action=new -> show "create user" form (userForm.jsp)
 *  GET  /users?action=edit&id=N -> show "edit user" form (userForm.jsp)
 *  POST /users            -> create or update a user (based on hidden "id" field)
 *  GET  /users?action=delete&id=N -> delete a user
 */
@WebServlet("/users")
public class UserServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        if (action == null) action = "list";

        try {
            switch (action) {
                case "new":
                    request.setAttribute("user", new User());
                    request.getRequestDispatcher("/userForm.jsp").forward(request, response);
                    break;

                case "edit":
                    int editId = Integer.parseInt(request.getParameter("id"));
                    User user = userDAO.findById(editId);
                    request.setAttribute("user", user);
                    request.getRequestDispatcher("/userForm.jsp").forward(request, response);
                    break;

                case "delete":
                    int deleteId = Integer.parseInt(request.getParameter("id"));
                    userDAO.delete(deleteId);
                    response.sendRedirect("users");
                    break;

                default: // "list"
                    request.setAttribute("users", userDAO.findAll());
                    request.getRequestDispatcher("/users.jsp").forward(request, response);
                    break;
            }
        } catch (SQLException | NumberFormatException e) {
            throw new ServletException("Database error: " + e.getMessage(), e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String idParam = request.getParameter("id");
        String msisdn = request.getParameter("msisdn");
        String balanceParam = request.getParameter("balance");

        try {
            User user = new User();
            user.setMsisdn(msisdn);
            user.setBalance(new BigDecimal(balanceParam));

            if (idParam != null && !idParam.isEmpty()) {
                // Update existing user
                user.setId(Integer.parseInt(idParam));
                userDAO.update(user);
            } else {
                // Create new user
                userDAO.create(user);
            }

            response.sendRedirect("users");

        } catch (SQLException | NumberFormatException e) {
            throw new ServletException("Database error: " + e.getMessage(), e);
        }
    }
}
