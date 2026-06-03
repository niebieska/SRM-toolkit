#!/bin/bash

echo "Starting Registration API..."
gnome-terminal -- bash -c "
cd backend/registration-api
mvn spring-boot:run
exec bash
"

echo "Starting Biuro API..."
gnome-terminal -- bash -c "
cd backend/biuro-api
mvn spring-boot:run
exec bash
"

echo "Starting Email Service..."
gnome-terminal -- bash -c "
cd backend/email-service
mvn spring-boot:run
exec bash
"

echo "Starting Registration Frontend..."
gnome-terminal -- bash -c "
cd frontend/registration-frontend
npm run dev
exec bash
"

echo "Starting Biuro Frontend..."
gnome-terminal -- bash -c "
cd frontend/biuro-frontend
npm run dev
exec bash
"