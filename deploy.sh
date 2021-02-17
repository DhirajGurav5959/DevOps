#!/bin/bash

RED="\033[1;31m"
GREEN="\033[1;32m"
DEFAULT="\033[0m"

echo -e "${GREEN}Starting the Deployment Process${DEFAULT}" && \
echo -e "${GREEN}Updating Docker Compose service version ${DEFAULT}" 
sleep 1 
sed -i "s/PLATFORM_VERSION/$1/g" docker-compose.yml && \
sleep 5 && \
sed -i "s/CONTAINER_USER/$2/g" docker-compose.yml && \
sleep 5 && \
cp docker-compose.yml /home/$2/.web-sg/
echo -e "${GREEN}Starting Service${DEFAULT}" && \
cd /home/$2/.web-sg/ && docker-compose up -d && \
echo -e "${GREEN}Service Started${DEFAULT}"