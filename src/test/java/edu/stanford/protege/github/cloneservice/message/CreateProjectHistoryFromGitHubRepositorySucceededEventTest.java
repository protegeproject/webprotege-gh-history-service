package edu.stanford.protege.github.cloneservice.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.stanford.protege.webprotege.common.BlobLocation;
import edu.stanford.protege.webprotege.common.EventId;
import edu.stanford.protege.webprotege.common.ProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;
import org.springframework.boot.test.json.ObjectContent;

class CreateProjectHistoryFromGitHubRepositorySucceededEventTest {

    private JacksonTester<CreateProjectHistorySucceededEvent> json;

    @BeforeEach
    void setup() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        JacksonTester.initFields(this, objectMapper);
    }

    @Test
    void channel_shouldMatchConstant() {
        var evt = fixture();
        assertThat(evt.getChannel()).isEqualTo("webprotege.events.github.CreateProjectHistorySucceeded");
    }

    @Test
    void shouldSerializeToExpectedJson() throws Exception {
        var evt = fixture();

        JsonContent<CreateProjectHistorySucceededEvent> jsonContent = json.write(evt);

        assertThat(jsonContent)
                .extractingJsonPathStringValue("$.eventId")
                .isEqualTo(evt.eventId().id());
        assertThat(jsonContent)
                .extractingJsonPathStringValue("$.operationId")
                .isEqualTo(evt.operationId().operationId());
        assertThat(jsonContent)
                .extractingJsonPathStringValue("$.projectId")
                .isEqualTo(evt.projectId().value());
        assertThat(jsonContent)
                .extractingJsonPathStringValue("$.documentLocation.bucket")
                .isEqualTo(evt.documentLocation().bucket());
        assertThat(jsonContent)
                .extractingJsonPathStringValue("$.documentLocation.name")
                .isEqualTo(evt.documentLocation().name());
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        var evt = fixture();
        String content =
                """
            {
              "@type" : "webprotege.events.github.CreateProjectHistorySucceeded",
              "eventId": "%s",
              "operationId": "%s",
              "projectId": "%s",
              "documentLocation": {
                                     "bucket" : "%s",
                                     "name": "%s"
                                  }
            }
            """
                        .formatted(
                                evt.eventId().id(),
                                evt.operationId().operationId(),
                                evt.projectId().id(),
                                evt.documentLocation().bucket(),
                                evt.documentLocation().name());

        ObjectContent<CreateProjectHistorySucceededEvent> parsed = json.parse(content);

        assertThat(parsed.getObject()).isEqualTo(evt);
    }

    private static CreateProjectHistorySucceededEvent fixture() {
        return new CreateProjectHistorySucceededEvent(
                EventId.generate(),
                CreateProjectHistoryOperationId.generate(),
                ProjectId.generate(),
                new BlobLocation("TheBucketName", "TheName"));
    }
}
