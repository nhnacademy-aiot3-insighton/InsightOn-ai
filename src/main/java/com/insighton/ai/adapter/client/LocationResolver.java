package com.insighton.ai.adapter.client;

import com.insighton.ai.adapter.client.dto.LocationResponse;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이름으로 위치를 찾는 로직. ActuatorChatTool/TelemetryStatChatTool이 각자 들고 있던 걸,
 * 세 번째 소비처(ScheduledComfortSetupChatTool)가 생기면서 공용으로 뺐다.
 */
@Component
@RequiredArgsConstructor
public class LocationResolver {

    private final CoreClient coreClient;

    public Optional<Long> resolveIdByName(Long groupId, String locationName) {
        List<LocationResponse> locations = coreClient.getLocationsByGroup(groupId);

        Optional<LocationResponse> exactMatch = locations.stream()
                .filter(location -> location.locationName().equalsIgnoreCase(locationName))
                .findFirst();

        if (exactMatch.isPresent()) {
            return exactMatch.map(LocationResponse::locationId);
        }

        String normalizedQuery = locationName.toLowerCase(Locale.ROOT);
        return locations.stream()
                .filter(location -> location.locationName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(LocationResponse::locationId)
                .findFirst();
    }
}
