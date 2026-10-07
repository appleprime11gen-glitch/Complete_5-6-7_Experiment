package com.example.postapi.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document(collection = "posts")
@CompoundIndexes({
    @CompoundIndex(name = "posts_created_at_id", def = "{'createdAt': -1, '_id': -1}"),
    @CompoundIndex(name = "posts_content_id_stable", def = "{'content': 1, '_id': 1}")
})
public class Post {

    @Id
    private String id;

    private String content;

    private String ownerId;

    private LocalDateTime createdAt;

    public Post(String content) {
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
