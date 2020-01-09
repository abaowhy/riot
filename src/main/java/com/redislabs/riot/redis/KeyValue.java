package com.redislabs.riot.redis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public @Data class KeyValue {

	private String key;
	private long ttl;
	private byte[] value;

}