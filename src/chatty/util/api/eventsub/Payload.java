
package chatty.util.api.eventsub;

import chatty.util.api.eventsub.payloads.PollPayload;
import chatty.util.api.eventsub.payloads.RaidPayload;
import chatty.util.api.eventsub.payloads.SubscriptionPayload;
import chatty.util.api.eventsub.payloads.SessionPayload;
import java.util.Map;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

/**
 * Data of a message. Different subclasses contain topic specific data.
 * 
 * @author tduva
 */
public class Payload {

    public final long created_at = System.currentTimeMillis();

    public static Payload decode(JSONObject payload, Map<String, String> userIds, String msgType, String subType) throws ParseException {
        if (payload == null) {
            return null;
        }
        switch (msgType) {
            case "session_welcome":
            case "session_reconnect":
                return SessionPayload.decode(payload);
            case "revocation":
                return SubscriptionPayload.decode(payload);
        }
        
        if (subType != null) {
            switch (subType) {
                case "channel.raid":
                    return RaidPayload.decode(payload);
                case "channel.poll.begin":
                case "channel.poll.end":
                    return PollPayload.decode(payload);
            }
        }
        
        return null;
    }

}
