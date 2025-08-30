APP            := $(shell basename $(shell git remote get-url origin) | sed 's/\.git$$//')
SHORT_SHA      := $(shell git rev-parse --short HEAD 2>/dev/null || echo unknown)
# Витягти appVersion без лапок з helm/Chart.yaml
TAG_ONLY   := $(shell \
	( git describe --tags --abbrev=0 2>/dev/null ) \
	|| ( git tag --sort=-creatordate | tail -n1 ) \
	|| echo v0.0.0 )

# APP_VERSION    := $(shell git describe --tags --abbrev=0)-$(shell git rev-parse --short HEAD)
VERSION        := $(TAG_ONLY)-$(SHORT_SHA)

REGISTRY       ?= ghcr.io/kors-dev
IMAGE          := $(REGISTRY)/$(APP)

TARGETOS       ?= linux
TARGETARCH     ?= amd64
BIN            := kbot$(if $(filter $(TARGETOS),windows),.exe,)

.PHONY: format get lint test build image push clean

format:
	gofmt -s -w ./

get:
	go mod tidy

lint:
	golint ./...

test:
	go test -v ./...

build: format get
	CGO_ENABLED=0 GOOS=$(TARGETOS) GOARCH=$(TARGETARCH) \
	go build -v -o $(BIN) \
	  -ldflags "-s -w -X github.com/kors-dev/kbot/cmd.appVersion=$(VERSION)" \
	  .

image:
	docker build . \
	  --build-arg TARGETOS=$(TARGETOS) \
	  --build-arg TARGETARCH=$(TARGETARCH) \
	  -t $(IMAGE):$(VERSION)-$(TARGETOS)-$(TARGETARCH)

push:
	docker push $(IMAGE):$(VERSION)-$(TARGETOS)-$(TARGETARCH)

clean:
	rm -f kbot kbot.exe


print-version:
	@echo "TAG_ONLY=$(TAG_ONLY)"
	@echo "SHORT_SHA=$(SHORT_SHA)"
	@echo "VERSION=$(VERSION)"