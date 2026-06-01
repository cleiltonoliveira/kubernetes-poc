.PHONY: install run

install:
	./gradlew classes

run:
	./gradlew bootRun

jar:
	./gradlew clean bootJar

image: jar
	docker build -t auth-api:v1 .

	