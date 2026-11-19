# Webhook relay: receives GitHub events from a smee.io channel over an outbound connection
# and forwards them to Jenkins on the internal Compose network, so Jenkins needs no public address.
# Versions pinned; the channel URL and target are passed by compose.yml.
FROM node:24.22.3-alpine3.24

RUN npm install -g smee-client@5.0.0 \
    && npm cache clean --force

USER node
ENTRYPOINT ["smee"]