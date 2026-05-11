package security

import (
	"context"
	"errors"
	"testing"

	"github.com/golang-jwt/jwt/v5"

	"modern-user-product-order-system/gateway-go/internal/config"
)

type fakeBlacklist struct {
	blacklisted bool
	token       string
}

func (f *fakeBlacklist) IsTokenBlacklisted(ctx context.Context, token string) bool {
	f.token = token
	return f.blacklisted
}

func testConfig() config.Config {
	return config.Config{
		JWTSecret:    "secret",
		JWTAlgorithm: "HS256",
	}
}

func makeToken(t *testing.T, secret string, claims jwt.MapClaims) string {
	t.Helper()
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString([]byte(secret))
	if err != nil {
		t.Fatalf("failed to sign token: %v", err)
	}
	return signed
}

func TestGetCurrentUserReturnsNilWhenAuthorizationMissing(t *testing.T) {
	service := New(testConfig(), nil)

	user, err := service.GetCurrentUser(context.Background(), "")

	if err != nil {
		t.Fatalf("unexpected err: %v", err)
	}
	if user != nil {
		t.Fatalf("expected nil user")
	}
}

func TestGetCurrentUserRejectsMalformedAuthorization(t *testing.T) {
	service := New(testConfig(), nil)

	_, err := service.GetCurrentUser(context.Background(), "Token abc")

	if !errors.Is(err, ErrAuthorizationBearerRequired) {
		t.Fatalf("expected bearer error, got %v", err)
	}
}

func TestDecodeTokenReturnsCurrentUser(t *testing.T) {
	service := New(testConfig(), &fakeBlacklist{})
	token := makeToken(t, "secret", jwt.MapClaims{
		"user_id":  float64(42),
		"username": "john_smith",
		"role":     "USER",
	})

	user, err := service.GetCurrentUser(context.Background(), "Bearer "+token)

	if err != nil {
		t.Fatalf("unexpected err: %v", err)
	}
	if user.UserID != 42 || user.Username != "john_smith" || user.Role != "USER" {
		t.Fatalf("unexpected user: %#v", user)
	}
}

func TestDecodeTokenRejectsInvalidToken(t *testing.T) {
	service := New(testConfig(), nil)

	_, err := service.GetCurrentUser(context.Background(), "Bearer invalid")

	if !errors.Is(err, ErrInvalidAccessToken) {
		t.Fatalf("expected invalid token error, got %v", err)
	}
}

func TestDecodeTokenRejectsBlacklistedToken(t *testing.T) {
	blacklist := &fakeBlacklist{blacklisted: true}
	service := New(testConfig(), blacklist)
	token := makeToken(t, "secret", jwt.MapClaims{
		"user_id":  float64(42),
		"username": "john_smith",
		"role":     "USER",
	})

	_, err := service.GetCurrentUser(context.Background(), "Bearer "+token)

	if !errors.Is(err, ErrAccessTokenRevoked) {
		t.Fatalf("expected revoked token error, got %v", err)
	}
	if blacklist.token != token {
		t.Fatalf("expected blacklist to check token")
	}
}
