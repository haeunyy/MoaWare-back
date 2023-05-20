package com.greedy.moaware.project.repository;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.greedy.moaware.project.entity.Task;

public interface TaskRepository extends JpaRepository<Task, Integer>{

	List<Task> findByProjectAndStatus(Integer projCode, String string, Sort sort);

	@Query("SELECT t "
			+ "FROM Task t "
			+ "JOIN fetch t.project "
			+ "WHERE t.project.projCode = :projCode")
	List<Task> findByAll(@Param("projCode") Integer projCode);

}
