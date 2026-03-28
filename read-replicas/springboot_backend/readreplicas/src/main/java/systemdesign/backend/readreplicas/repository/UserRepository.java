package systemdesign.backend.readreplicas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import systemdesign.backend.readreplicas.model.User;

public interface UserRepository extends JpaRepository<User, Integer> {
	
}
