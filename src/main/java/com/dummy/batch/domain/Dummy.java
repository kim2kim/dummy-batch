package com.dummy.batch.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 
 * @author khimung
 *
 */
@Entity
@Table(name = "dummy")
public class Dummy {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	private int level;

	public Dummy() {
	}

	public Dummy(int level) {
		this.level = level;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

}
