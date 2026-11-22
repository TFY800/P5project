#!/bin/sh
# Optional repository-local installation. No sudo, Homebrew or changes to existing PostgreSQL.
set -eu
cd "$(dirname "$0")/../.."
cache="$PWD/.build-cache"
version=17.6
if [ -x "$cache/postgres/bin/postgres" ]; then
    "$cache/postgres/bin/postgres" --version
    exit 0
fi
mkdir -p "$cache"
curl -fL "https://ftp.postgresql.org/pub/source/v$version/postgresql-$version.tar.bz2" -o "$cache/postgresql-$version.tar.bz2"
curl -fL "https://ftp.postgresql.org/pub/source/v$version/postgresql-$version.tar.bz2.sha256" -o "$cache/postgresql-$version.tar.bz2.sha256"
(cd "$cache" && shasum -a 256 -c "postgresql-$version.tar.bz2.sha256")
mkdir -p "$cache/postgres-src"
tar -xjf "$cache/postgresql-$version.tar.bz2" -C "$cache/postgres-src" --strip-components=1
cd "$cache/postgres-src"
./configure --prefix="$cache/postgres" --without-icu --without-readline --without-zlib
make -j4
make install
