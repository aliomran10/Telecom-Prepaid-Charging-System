Place the PostgreSQL JDBC driver jar here (e.g. postgresql-42.7.3.jar).

Download from: https://jdbc.postgresql.org/download/

This sandbox could not download it directly (Maven Central is not in the
allowed network domains here), so you'll need to fetch it on your own machine
and add it to the NetBeans project's Libraries (for msc-app and web-app).

If using Maven, the dependency is already declared in msc-app/pom.xml and
web-app/pom.xml and will be downloaded automatically.
