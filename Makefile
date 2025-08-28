APP            := $(shell basename $(shell git remote get-url origin) | sed 's/\.git$$//')
GIT_SHA        := $(shell git rev-parse --short HEAD)
APP_VERSION    := $(shell awk '/^appVersion:/{print $$2}' helm/Chart.yaml 2>/dev/null)
VERSION        := $(if $(APP_VERSION),$(APP_VERSION),v0.0.0)-$(GIT_SHA)

REGISTRY       ?= ghcr.io/kors-dev           
IMAGE          := $(REGISTRY)/$(APP)

TARGETOS       ?= linux
TARGETARCH     ?= amd64

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
	go build -v -o kbot \
	  -ldflags "-s -w -X github.com/kors-dev/kbot/cmd.appVersion=$(VERSION)" \
	  ./...

image:
	docker build . \
	  --build-arg TARGETOS=$(TARGETOS) \
	  --build-arg TARGETARCH=$(TARGETARCH) \
	  -t $(IMAGE):$(VERSION)-$(TARGETOS)-$(TARGETARCH)

push:
	docker push $(IMAGE):$(VERSION)-$(TARGETOS)-$(TARGETARCH)

clean:
	rm -f kbot
