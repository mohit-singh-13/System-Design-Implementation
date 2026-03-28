package systemdesign.backend.readreplicas.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import systemdesign.backend.readreplicas.dto.UserDTO;
import systemdesign.backend.readreplicas.model.User;
import systemdesign.backend.readreplicas.repository.UserRepository;

@Service
public class UserService {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Transactional
	public void createUser(UserDTO userDto) {
		User user = new User();
		user.setId(userDto.getId());
		user.setName(userDto.getName());
		
		// Query to identify which DB instance this query hit
		String dbInstance = jdbcTemplate.queryForObject("SELECT @@server_id", String.class);
		System.out.println("DB USED (CREATE USER) : " + dbInstance);

		userRepository.save(user);

	}

	@Transactional(readOnly = true)
	public User getUser(Integer id) {
		User user = userRepository.findById(id).orElse(null);

		// Query to identify which DB instance this query hit
		String dbInstance = jdbcTemplate.queryForObject("SELECT @@server_id", String.class);
		System.out.println("DB USED (GET USER) : " + dbInstance);

		return user;
	}
}
