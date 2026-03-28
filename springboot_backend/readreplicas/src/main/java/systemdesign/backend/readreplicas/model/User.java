package systemdesign.backend.readreplicas.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity(name = "user")
@Data
public class User {
	
	@Id
	private Integer id;
	private String name;
}
