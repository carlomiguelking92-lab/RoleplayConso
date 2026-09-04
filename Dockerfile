FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY Desktop.jar app.jar
COPY dashbord.html ./
COPY *.png ./
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
