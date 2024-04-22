package ca.qc.cvm.dba.persinteret.entity;

import java.util.List;

public class Person {
	private String id;
	private String name;
	private String codeName;
	private String dateOfBirth;
	private String status;
	private List<String> connexions;
	private byte[] imageData;
	
	public Person() {
		
	}
	
	public Person(String name, String codeName, String status, String dob, List<String> connexions, byte[] img) {
		this.name = name;
		this.codeName = codeName;
		this.dateOfBirth = dob;
		this.status = status;
		this.connexions = connexions;
		this.imageData = img;
	}
	
	public Person(String id, String name, String codeName, String status, String dob, List<String> connexions, byte[] img) {
		this.id = id;
		this.name = name;
		this.codeName = codeName;
		this.dateOfBirth = dob;
		this.status = status;
		this.connexions = connexions;
		this.imageData = img;
	}
	
	public String getDateOfBirth() {
		return dateOfBirth;
	}

	public void setDateOfBirth(String dateOfBirth) {
		this.dateOfBirth = dateOfBirth;
	}

	public List<String> getConnexions() {
		return connexions;
	}

	public void setConnexions(List<String> connexions) {
		this.connexions = connexions;
	}

	public String getCodeName() {
		return codeName;
	}

	public void setCodeName(String codeName) {
		this.codeName = codeName;
	}

	public byte[] getImageData() {
		return imageData;
	}

	public String getName() {
		return name;
	}

	public String getStatus() {
		return status;
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void setImageData(byte[] imageData) {
		this.imageData = imageData;
	}

	public String toString() {
		return String.format("%-20s%-20s%-10s%-12s%-15s", name, codeName, status, dateOfBirth, (connexions == null ? 0 : connexions.size()) + " connexions");
	}
}
