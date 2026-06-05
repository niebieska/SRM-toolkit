#!/bin/bash

set -e

echo "==================================="
echo "Building backend services"
echo "==================================="

mvn clean install

echo ""
echo "==================================="
echo "Installing frontend dependencies"
echo "==================================="

cd frontend/registration-frontend
npm install
npm run build

cd ../biuro-frontend
npm install
npm run build

cd ../..

echo ""
echo "==================================="
echo "Build completed successfully"
echo "==================================="