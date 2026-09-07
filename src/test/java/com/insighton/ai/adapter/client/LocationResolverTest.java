package com.insighton.ai.adapter.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationResolverTest {

    @Mock
    private CoreClient coreClient;

    @InjectMocks
    private LocationResolver locationResolver;

    @Test
    void resolveIdByName_대소문자_무관하게_정확히_일치하면_해당_위치를_반환한다() {
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(1L, "3층 회의실", 5L, AutoControlMode.SUGGESTION),
                new LocationResponse(2L, "옆 회의실", 5L, AutoControlMode.SUGGESTION)));

        Optional<Long> resolved = locationResolver.resolveIdByName(5L, "3층 회의실");

        assertThat(resolved).contains(1L);
    }

    @Test
    void resolveIdByName_정확히_일치하는_게_없으면_부분_일치로_찾는다() {
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(1L, "3층 대회의실", 5L, AutoControlMode.SUGGESTION)));

        Optional<Long> resolved = locationResolver.resolveIdByName(5L, "회의실");

        assertThat(resolved).contains(1L);
    }

    @Test
    void resolveIdByName_정확_일치가_있으면_부분_일치보다_우선한다() {
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(1L, "회의실", 5L, AutoControlMode.SUGGESTION),
                new LocationResponse(2L, "3층 회의실 옆방", 5L, AutoControlMode.SUGGESTION)));

        Optional<Long> resolved = locationResolver.resolveIdByName(5L, "회의실");

        assertThat(resolved).contains(1L);
    }

    @Test
    void resolveIdByName_일치하는_위치가_없으면_빈_Optional을_반환한다() {
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(1L, "3층 회의실", 5L, AutoControlMode.SUGGESTION)));

        Optional<Long> resolved = locationResolver.resolveIdByName(5L, "옥상 정원");

        assertThat(resolved).isEmpty();
    }
}
