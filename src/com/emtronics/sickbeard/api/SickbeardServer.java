package com.emtronics.sickbeard.api;

public class SickbeardServer {
	private String name;
	private String host;
	private String port;
	private String api;
	private boolean ssl;
	@Override
	public String toString() {
		return "SickbeardServerSettings [name=" + name + ", host=" + host
				+ ", port=" + port + ", api=" + api + ", ssl=" + ssl + "]";
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getHost() {
		if (ssl)
			return "https://" + host;
		else
			return "http://" + host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public String getPort() {
		return port;
	}
	public void setPort(String port) {
		this.port = port;
	}
	public String getApi() {
		return api;
	}
	public void setApi(String api) {
		this.api = api;
	}
	public boolean isSsl() {
		return ssl;
	}
	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}
	
}
