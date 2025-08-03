package io.oci.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "manifests")
public class Manifest extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    @JoinColumn(name = "repository_id", nullable = false)
    public Repository repository;

    @Column(nullable = false)
    public String digest;

    @Column(name = "media_type", nullable = false)
    public String mediaType;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String content;

    @Column(name = "content_length")
    public Long contentLength;

    public String tag;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
