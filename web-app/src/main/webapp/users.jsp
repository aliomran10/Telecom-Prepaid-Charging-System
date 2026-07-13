<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.telecom.web.model.User" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Prepaid Charging - Users</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
<div class="container">
    <h1>Prepaid Charging - User Management</h1>

    <div class="toolbar">
        <a class="btn btn-primary" href="users?action=new">+ Add New User</a>
    </div>

    <table>
        <thead>
        <tr>
            <th>ID</th>
            <th>MSISDN</th>
            <th>Balance (L.E)</th>
            <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        <%
            List<User> users = (List<User>) request.getAttribute("users");
            if (users != null) {
                for (User u : users) {
        %>
        <tr>
            <td><%= u.getId() %></td>
            <td><%= u.getMsisdn() %></td>
            <td class="<%= u.getBalance().doubleValue() < 5 ? "balance-low" : "" %>">
                <%= u.getBalance() %>
            </td>
            <td class="actions">
                <a class="btn btn-edit" href="users?action=edit&id=<%= u.getId() %>">Edit</a>
                <a class="btn btn-delete" href="users?action=delete&id=<%= u.getId() %>"
                   onclick="return confirm('Delete user <%= u.getMsisdn() %>?');">Delete</a>
            </td>
        </tr>
        <%
                }
            }
        %>
        </tbody>
    </table>
</div>
</body>
</html>
