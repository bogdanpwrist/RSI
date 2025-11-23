# PowerShell script to deploy the Email Pipeline to Kubernetes

Write-Host "Building Docker images..." -ForegroundColor Green

# Build images
docker build -t grpc-service:latest -f grpc-service/Dockerfile .
docker build -t rest-api:latest -f rest-api/Dockerfile .
docker build -t consumer-service:latest -f consumer-service/Dockerfile .

Write-Host "Creating namespace..." -ForegroundColor Green
kubectl apply -f k8s/namespace.yaml

Write-Host "Deploying databases..." -ForegroundColor Green
kubectl apply -f k8s/postgres-gmail.yaml
kubectl apply -f k8s/postgres-wp.yaml
kubectl apply -f k8s/postgres-other.yaml

Write-Host "Waiting for databases to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

Write-Host "Deploying RabbitMQ..." -ForegroundColor Green
kubectl apply -f k8s/rabbitmq-deployment.yaml

Write-Host "Waiting for RabbitMQ to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 20

Write-Host "Deploying gRPC service..." -ForegroundColor Green
kubectl apply -f k8s/grpc-service-deployment.yaml

Write-Host "Waiting for gRPC service to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host "Deploying REST API..." -ForegroundColor Green
kubectl apply -f k8s/rest-api-deployment.yaml

Write-Host "Deploying consumers..." -ForegroundColor Green
kubectl apply -f k8s/consumer-gmail-deployment.yaml
kubectl apply -f k8s/consumer-wp-deployment.yaml
kubectl apply -f k8s/consumer-other-deployment.yaml

Write-Host "Deploying frontend..." -ForegroundColor Green
kubectl apply -f k8s/frontend-deployment.yaml

Write-Host "`nDeployment complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Access points:" -ForegroundColor Cyan
Write-Host "- Frontend: http://localhost:30800"
Write-Host "- REST API: http://localhost:30700"
Write-Host "- RabbitMQ Management: http://localhost:30672 (guest/guest)"
Write-Host ""
Write-Host "To check status: kubectl get pods -n email-pipeline" -ForegroundColor Yellow
Write-Host "To view logs: kubectl logs -n email-pipeline <pod-name>" -ForegroundColor Yellow
