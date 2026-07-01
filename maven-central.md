# Maven Central Publishing Setup

## Context

`openapi-spring-ai-tools` is a Java library (not an application) that exposes OpenAPI 3 operations as Spring AI `ToolCallback` instances at runtime. It is built with Maven.

This document describes everything needed to prepare the project for Maven Central publishing. The project already has a complete source tree and a working `pom.xml`. The task is purely about publishing infrastructure.

---

## Coordinates

```
groupId:    io.github.anshake
artifactId: openapi-spring-ai-tools
```

The namespace `io.github.anshake` must be registered and verified on the Central Portal (`central.sonatype.com`) before publishing. Verification requires creating a public GitHub repository with the name provided by the Portal (one-time step, the repo can be deleted afterward). This is a manual step that cannot be automated.

---

## Goals

### Release (triggered by tag push)

- Strip `-SNAPSHOT` from the version
- Build and test
- Create a GitHub Release with the JAR attached

No Maven Central or Central Portal involvement.

### Deploy (triggered manually via `workflow_dispatch`, requires a tag name as input)

- Sign artifacts with GPG
- Upload to Central Portal staging
- Auto-publish to Maven Central (`autoPublish=true`)

---

## pom.xml Changes

The following must be added to the existing `pom.xml`. Do not remove or restructure anything already there.

### 1. Add top-level metadata (after `<description>`)

```xml
<url>https://github.com/anshake/openapi-spring-ai-tools</url>

<licenses>
    <license>
        <name>Apache-2.0</name>
        <url>https://www.apache.org/licenses/LICENSE-2.0</url>
    </license>
</licenses>

<developers>
    <developer>
        <id>anshake</id>
        <name>Shake</name>
        <url>https://github.com/anshake</url>
    </developer>
</developers>

<scm>
    <connection>scm:git:git://github.com/anshake/openapi-spring-ai-tools.git</connection>
    <developerConnection>scm:git:ssh://git@github.com/anshake/openapi-spring-ai-tools.git</developerConnection>
    <url>https://github.com/anshake/openapi-spring-ai-tools</url>
</scm>
```

### 2. Add plugin version properties

Add to the existing `<properties>` block:

```xml
<maven-source-plugin.version>3.3.1</maven-source-plugin.version>
<maven-javadoc-plugin.version>3.11.2</maven-javadoc-plugin.version>
<maven-gpg-plugin.version>3.2.7</maven-gpg-plugin.version>
<central-publishing-maven-plugin.version>0.9.0</central-publishing-maven-plugin.version>
```

Note: `maven-compiler-plugin.version` is already managed by the Spring Boot BOM — remove it from `<properties>` and remove the explicit `<version>` from the existing `maven-compiler-plugin` declaration in `<build><plugins>`. The BOM version will apply automatically.

### 3. Add plugins (inside the existing `<build><plugins>` block)

```xml
<!-- Sources JAR — required by Maven Central -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-source-plugin</artifactId>
    <version>${maven-source-plugin.version}</version>
    <executions>
        <execution>
            <id>attach-sources</id>
            <goals>
                <goal>jar-no-fork</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<!-- Javadoc JAR — required by Maven Central -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>${maven-javadoc-plugin.version}</version>
    <executions>
        <execution>
            <id>attach-javadocs</id>
            <goals>
                <goal>jar</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<!-- GPG signing — required by Maven Central -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-gpg-plugin</artifactId>
    <version>${maven-gpg-plugin.version}</version>
    <executions>
        <execution>
            <id>sign-artifacts</id>
            <phase>verify</phase>
            <goals>
                <goal>sign</goal>
            </goals>
            <configuration>
                <gpgArguments>
                    <arg>--pinentry-mode</arg>
                    <arg>loopback</arg>
                </gpgArguments>
            </configuration>
        </execution>
    </executions>
</plugin>

<!-- Central Portal publisher -->
<plugin>
    <groupId>org.sonatype.central</groupId>
    <artifactId>central-publishing-maven-plugin</artifactId>
    <version>${central-publishing-maven-plugin.version}</version>
    <extensions>true</extensions>
    <configuration>
        <publishingServerId>central</publishingServerId>
        <autoPublish>true</autoPublish>
    </configuration>
</plugin>
```

---

## GitHub Actions Workflows

Create two separate workflow files.

### `.github/workflows/release.yml`

Triggered by a tag push matching `v*`. Strips `-SNAPSHOT`, builds, tests, and creates a GitHub Release with the JAR attached.

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Extract version from tag
        id: version
        run: echo "VERSION=${GITHUB_REF_NAME#v}" >> $GITHUB_OUTPUT

      - name: Set release version
        run: mvn versions:set -DnewVersion=${{ steps.version.outputs.VERSION }} -DgenerateBackupPoms=false

      - name: Build and test
        run: mvn clean verify -DskipGpg=true

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ github.ref_name }}
          name: ${{ github.ref_name }}
          files: target/openapi-spring-ai-tools-${{ steps.version.outputs.VERSION }}.jar
          generate_release_notes: true
```

### `.github/workflows/deploy.yml`

Triggered manually. Requires a tag name as input. Checks out that tag, sets the version, signs artifacts, uploads to Central Portal, and auto-publishes to Maven Central.

```yaml
name: Deploy to Maven Central

on:
  workflow_dispatch:
    inputs:
      tag:
        description: 'Tag to deploy (e.g. v0.1.0)'
        required: true

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout tag
        uses: actions/checkout@v4
        with:
          ref: ${{ github.event.inputs.tag }}

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Import GPG key
        run: echo "${{ secrets.GPG_PRIVATE_KEY }}" | gpg --batch --import

      - name: Extract version from tag
        id: version
        run: echo "VERSION=${GITHUB_REF_NAME#v}" >> $GITHUB_OUTPUT

      - name: Set release version
        run: mvn versions:set -DnewVersion=${{ steps.version.outputs.VERSION }} -DgenerateBackupPoms=false

      - name: Deploy to Maven Central
        run: mvn --batch-mode clean deploy
        env:
          MAVEN_USERNAME: ${{ secrets.CENTRAL_TOKEN_USERNAME }}
          MAVEN_PASSWORD: ${{ secrets.CENTRAL_TOKEN_PASSWORD }}
          MAVEN_GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}

      - name: Configure Maven settings
        uses: s4u/maven-settings-action@v3.0.0
        with:
          servers: |
            [{
              "id": "central",
              "username": "${{ secrets.CENTRAL_TOKEN_USERNAME }}",
              "password": "${{ secrets.CENTRAL_TOKEN_PASSWORD }}"
            }]
```

---

## GitHub Secrets Required

These must be set in the repository settings under **Settings → Secrets and variables → Actions**. They cannot be added by Claude Code — they require manual setup.

| Secret | Description |
|---|---|
| `GPG_PRIVATE_KEY` | ASCII-armored GPG private key. Export with: `gpg --armor --export-secret-keys YOUR_KEY_ID` |
| `GPG_PASSPHRASE` | Passphrase for the GPG key |
| `CENTRAL_TOKEN_USERNAME` | User token username from `central.sonatype.com` → Account → Generate User Token |
| `CENTRAL_TOKEN_PASSWORD` | User token password (same page) |

---

## Manual Prerequisites (cannot be automated)

These steps must be completed by the developer before the workflows can succeed:

1. **Register on Central Portal**: create an account at `central.sonatype.com`
2. **Claim namespace**: add `io.github.anshake`, verify by creating a public GitHub repo with the name the Portal provides, then delete it
3. **Generate GPG key**: `gpg --gen-key`, then distribute the public key: `gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID`
4. **Generate Central Portal user token**: Account → Generate User Token
5. **Add all four secrets** to the GitHub repository

---

## What Claude Code Should Do

1. Add the metadata block (`url`, `licenses`, `developers`, `scm`) to `pom.xml`
2. Remove `maven-compiler-plugin.version` from `<properties>` and remove the explicit `<version>` from the existing `maven-compiler-plugin` declaration — the Spring Boot BOM manages it
3. Add the four publishing plugin version properties to `<properties>`
4. Add the four publishing plugins to `<build><plugins>`
5. Create `.github/workflows/release.yml`
6. Create `.github/workflows/deploy.yml`
7. Verify the `pom.xml` is valid and well-formed after changes

Do not modify anything else in the project.