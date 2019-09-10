# Description
This example is based on [spring-security-basic-auth](../spring-security-basic-auth/README.md) with two small changes.

It adds a dependency to java-container security. 
```xml
<groupId>com.sap.cloud.security.xsuaa</groupId>
<artifactId>java-container-security</artifactId>
<version>2.21.0</version>
```

And it returns the email of the current user from the SecurityContext object instead from the token object.
```java
	@GetMapping("/hello-token")
	public String message(@AuthenticationPrincipal Token token)  {
	    try {
            UserInfo userInfo = SecurityContext.getUserInfo();
            return "UserInfo: Email=" + userInfo.getEmail();
        } catch (UserInfoException e) {
            return "Could not get SecurityContext: " + e.getMessage();
        }
	}
```
This is possible because it uses an experimental version of the `cloud-security-xsuaa-integration` library which
synchronizes the token into the SecurityContext.

# Deployment on Cloud Foundry
To deploy the application, the following steps are required:
- Compile the Java application
- Create a xsuaa service instance
- Configure the manifest
- Deploy the application
- Access the application

## Compile the Java application
Run maven to package the application
```shell
mvn clean package
```

## Create the xsuaa service instance
Use the [xs-security.json](./xs-security.json) to define the authentication settings and create a service instance
```shell
cf create-service xsuaa application spring-security-synchronization -c xs-security.json
```

## Configuration the manifest
The [vars](../vars.yml) contains hosts and paths that need to be adopted.

## Deploy the application
Deploy the application using cf push. It will expect 1 GB of free memory quota.

```shell
cf push --vars-file ../vars.yml
```

## Access the application
After deployment, the spring service can be called with basic authentication.
```shell
curl -i --user "<SAP ID Service User>:<SAP ID Service Password>" https://spring-security-synchronization-<ID>.<LANDSCAPE_APPS_DOMAIN>/hello-token
```

You will get a response like:
```
UserInfo: Email=bob.jones@example.com"
```

## Clean-Up

Finally delete your application and your service instances using the following commands:
```
cf delete -f spring-security-synchronization
cf delete-service -f spring-security-synchronization
```

