package life.genny.adi.live.data;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import java.time.Duration;
import java.time.Instant;

import org.drools.core.common.InternalAgenda;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.kie.api.runtime.KieSession;
import org.kie.kogito.legacy.rules.KieRuntimeBuilder;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.reactive.messaging.annotations.Blocking;

import life.genny.qwandaq.models.GennyToken;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.serviceq.Service;

@ApplicationScoped
public class InternalConsumer {

	static final Logger log = Logger.getLogger(InternalConsumer.class);

    static Jsonb jsonb = JsonbBuilder.create();

    @Inject
    KieRuntimeBuilder ruleRuntime;

	@Inject
	Service service;

	/**
	* Execute on start up.
	*
	* @param ev
	 */
    void onStart(@Observes StartupEvent ev) {

		service.fullServiceInit();
		log.info("[*] Finished Startup!");
    }

	/**
	* Consume from the valid_data topic.
	*
	* @param data
	 */
    @Incoming("valid_data")
    @Blocking
    public void getValidData(String data) {

        log.infov("Incoming Valid Data : {}", data);
        Instant start = Instant.now();
        GennyToken userToken = null;
		BaseEntityUtils beUtils = service.getBeUtils();

        // deserialise to JsonObject
        JsonObject json = jsonb.fromJson(data, JsonObject.class);

        // check type of msg
        String msgType = json.getString("msg_type");
        String msgDataType = json.getString("data_type");

        if (!"DATA_MSG".equals(msgType) || (!"Answer".equals(msgDataType))) {
            log.error("Invalid message received!");
            return;
        }

        // check the token
        String token = json.getString("token");
        try {
            userToken = new GennyToken(token);
        } catch (Exception e) {
            log.error("Invalid Token!");
            return;
        }

        // update the token of our utility
        beUtils.setGennyToken(userToken);

        // init session and activate DataProcessing
        KieSession ksession = ruleRuntime.newKieSession();
        ((InternalAgenda) ksession.getAgenda()).activateRuleFlowGroup("DataProcessing");

        // insert facts into session
        ksession.insert(beUtils);

        // fire rules and dispose of session
        ksession.fireAllRules();
        ksession.dispose();

        Instant end = Instant.now();
        log.info("Duration = " + Duration.between(start, end).toMillis() + "ms");
    }
}
