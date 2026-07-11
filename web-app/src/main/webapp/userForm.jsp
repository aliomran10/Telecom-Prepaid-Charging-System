<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.telecom.web.model.User" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Prepaid Charging - User Form</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
<div class="container">
    <%
        User user = (User) request.getAttribute("user");
        boolean isEdit = (user != null && user.getId() != 0);
    %>
    <h1><%= isEdit ? "Edit User" : "Add New User" %></h1>

    <form class="user-form" action="users" method="post">
        <% if (isEdit) { %>
            <input type="hidden" name="id" value="<%= user.getId() %>">
        <% } %>

        <label for="msisdn">MSISDN</label>
        <input type="text" id="msisdn" name="msisdn" pattern="01[0125][0-9]{8}"
               title="11-digit Egyptian mobile number, e.g. 01223456789"
               value="<%= (user != null && user.getMsisdn() != null) ? user.getMsisdn() : "" %>" required>

        <label for="balance">Balance (L.E)</label>
        <input type="number" id="balance" name="balance" step="0.01" min="0"
               value="<%= (user != null && user.getBalance() != null) ? user.getBalance() : "0.00" %>" required>

        <button type="submit" class="btn btn-primary"><%= isEdit ? "Update" : "Create" %></button>
        <a class="btn btn-edit" href="users">Cancel</a>
    </form>
</div>
</body>
</html>
