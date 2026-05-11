package security

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"github.com/golang-jwt/jwt/v5"

	"modern-user-product-order-system/gateway-go/internal/config"
)

var (
	ErrAuthorizationBearerRequired = errors.New("Authorization header must use Bearer token")
	ErrInvalidAccessToken          = errors.New("Invalid access token")
	ErrAccessTokenRevoked          = errors.New("Access token has been revoked")
)

type BlacklistChecker interface {
	IsTokenBlacklisted(ctx context.Context, token string) bool
}

type CurrentUser struct {
	UserID   int
	Username string
	Role     string
}

type Service struct {
	cfg       config.Config
	blacklist BlacklistChecker
}

func New(cfg config.Config, blacklist BlacklistChecker) *Service {
	return &Service{cfg: cfg, blacklist: blacklist}
}

func (s *Service) DecodeToken(ctx context.Context, tokenString string) (*CurrentUser, error) {
	if s.blacklist != nil && s.blacklist.IsTokenBlacklisted(ctx, tokenString) {
		return nil, ErrAccessTokenRevoked
	}
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
		if token.Method.Alg() != s.cfg.JWTAlgorithm {
			return nil, fmt.Errorf("unexpected signing algorithm: %s", token.Method.Alg())
		}
		return []byte(s.cfg.JWTSecret), nil
	})
	if err != nil || !token.Valid {
		return nil, ErrInvalidAccessToken
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return nil, ErrInvalidAccessToken
	}
	userID, err := parseUserID(claims["user_id"])
	if err != nil {
		return nil, ErrInvalidAccessToken
	}
	username, ok := claims["username"].(string)
	if !ok {
		return nil, ErrInvalidAccessToken
	}
	role, ok := claims["role"].(string)
	if !ok {
		return nil, ErrInvalidAccessToken
	}
	return &CurrentUser{
		UserID:   userID,
		Username: username,
		Role:     role,
	}, nil
}

func (s *Service) GetCurrentUser(ctx context.Context, authorization string) (*CurrentUser, error) {
	if strings.TrimSpace(authorization) == "" {
		return nil, nil
	}
	scheme, token, ok := strings.Cut(authorization, " ")
	if !ok || strings.ToLower(scheme) != "bearer" || strings.TrimSpace(token) == "" {
		return nil, ErrAuthorizationBearerRequired
	}
	return s.DecodeToken(ctx, token)
}

func (s *Service) GetOptionalCurrentUser(ctx context.Context, authorization string) (*CurrentUser, error) {
	if strings.TrimSpace(authorization) == "" {
		return nil, nil
	}
	return s.GetCurrentUser(ctx, authorization)
}

func parseUserID(value interface{}) (int, error) {
	switch item := value.(type) {
	case float64:
		return int(item), nil
	case string:
		return strconv.Atoi(item)
	case jsonNumber:
		parsed, err := strconv.Atoi(item.String())
		if err != nil {
			return 0, err
		}
		return parsed, nil
	default:
		return 0, fmt.Errorf("unsupported user_id type %T", value)
	}
}

type jsonNumber interface {
	String() string
}
