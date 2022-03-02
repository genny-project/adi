package life.genny.adi.endpoints;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.persistence.EntityManager;

import org.jboss.logging.Logger;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.kogito.legacy.rules.KieRuntimeBuilder;
import org.drools.core.common.InternalAgenda;
import org.drools.core.impl.EnvironmentFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.vertx.core.http.HttpServerRequest;
import life.genny.adi.live.data.InternalConsumer;

/**
 * ADIResource - Endpoints providing ADI 
 */

@Path("/")
@ApplicationScoped
public class ADIResource {

	private static final Logger log = Logger.getLogger(ADIResource.class);

	@ConfigProperty(name = "project.version", defaultValue = "unknown")
	String version;

	@Context
	HttpServerRequest request;

	@Inject
	EntityManager entityManager;

    @Inject
    KieRuntimeBuilder ruleRuntime;

	// KieContainer kieContainer = KieServices.Factory.get().newKieClasspathContainer();

	Jsonb jsonb = JsonbBuilder.create();

	/**
	* A GET request for the running version
	*
	* @return 	version data
	 */
	@GET
	@Path("/api/version")
	public Response version() {
		return Response.ok().entity("version: " + version).build();
	}

	@GET
	@Path("/api/run")
	public Response run() {

        // init session and activate DataProcessing
        // KieSession ksession = ruleRuntime.newKieSession();
		KieBase kieBase = ruleRuntime.getKieBase();
		KieSessionConfiguration conf = KieServices.Factory.get().newKieSessionConfiguration();
		Environment environment = EnvironmentFactory.newEnvironment();
		KieSession kieSession = kieBase.newKieSession(conf, environment);
        // KieSession ksession = kieContainer.newKieSession();
        ((InternalAgenda) kieSession.getAgenda()).activateRuleFlowGroup("DataProcessing");

        // insert facts into session
        kieSession.insert("WORLD");
        kieSession.insert(log);

        // fire rules and dispose of session
        kieSession.fireAllRules();
        kieSession.dispose();

		return Response.ok().build();
	}
}
