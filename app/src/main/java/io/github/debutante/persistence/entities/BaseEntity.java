package io.github.debutante.persistence.entities;

public interface BaseEntity {
    String accountUuid();

    String uuid();

    String remoteUuid();

    String parentUuid();
}
