# PowerShell script to clean up Kubernetes deployment

Write-Host "Deleting all deployments and services..." -ForegroundColor Yellow

kubectl delete -f k8s/frontend-deployment.yaml
kubectl delete -f k8s/consumer-gmail-deployment.yaml
kubectl delete -f k8s/consumer-wp-deployment.yaml
kubectl delete -f k8s/consumer-other-deployment.yaml
kubectl delete -f k8s/rest-api-deployment.yaml
kubectl delete -f k8s/grpc-service-deployment.yaml
kubectl delete -f k8s/rabbitmq-deployment.yaml
kubectl delete -f k8s/postgres-gmail.yaml
kubectl delete -f k8s/postgres-wp.yaml
kubectl delete -f k8s/postgres-other.yaml

Write-Host "Deleting namespace..." -ForegroundColor Yellow
kubectl delete -f k8s/namespace.yaml

Write-Host "`nCleanup complete!" -ForegroundColor Green
