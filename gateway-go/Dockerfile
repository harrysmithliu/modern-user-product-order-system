FROM golang:1.22-alpine AS builder

WORKDIR /src

COPY gateway-go/go.mod gateway-go/go.sum ./
RUN go mod download

COPY gateway-go/ ./
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/gateway ./cmd/gateway

FROM alpine:3.20

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=builder /out/gateway /app/gateway

USER app

EXPOSE 8000

CMD ["/app/gateway"]
