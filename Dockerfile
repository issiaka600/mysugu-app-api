FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY .env /app/.env
COPY ./target/*.jar /app/mysugu-app-api.jar
EXPOSE 8083
CMD ["java","-jar","mysugu-app-api.jar"]