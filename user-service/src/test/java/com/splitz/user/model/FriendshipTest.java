package com.splitz.user.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for the Friendship entity. Tests status transitions and business logic methods. */
@DisplayName("Friendship Entity Tests")
class FriendshipTest {

  private User requester;
  private User addressee;

  @BeforeEach
  void setUp() {
    requester = new User();
    requester.setId(1L);
    requester.setUsername("requester");
    requester.setEmail("requester@test.com");
    requester.setFirstName("Requester");

    addressee = new User();
    addressee.setId(2L);
    addressee.setUsername("addressee");
    addressee.setEmail("addressee@test.com");
    addressee.setFirstName("Addressee");
  }

  @Nested
  @DisplayName("Factory Method Tests")
  class FactoryMethodTests {

    @Test
    @DisplayName("createRequest should create friendship with PENDING status")
    void createRequest_shouldCreatePendingFriendship() {
      // When
      Friendship friendship = Friendship.createRequest(requester, addressee);

      // Then
      assertThat(friendship.getRequester()).isEqualTo(requester);
      assertThat(friendship.getAddressee()).isEqualTo(addressee);
      assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    @DisplayName("createRequest with builder should work correctly")
    void builder_shouldCreateFriendshipCorrectly() {
      // When
      Friendship friendship =
          Friendship.builder()
              .requester(requester)
              .addressee(addressee)
              .status(FriendshipStatus.PENDING)
              .build();

      // Then
      assertThat(friendship.getRequester()).isEqualTo(requester);
      assertThat(friendship.getAddressee()).isEqualTo(addressee);
      assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }
  }

  @Nested
  @DisplayName("Status Transition Tests")
  class StatusTransitionTests {

    @Test
    @DisplayName("accept should change status from PENDING to ACCEPTED")
    void accept_fromPending_shouldChangeToAccepted() {
      // Given
      Friendship friendship = Friendship.createRequest(requester, addressee);
      assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);

      // When
      friendship.accept();

      // Then
      assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    @DisplayName("accept should throw exception when status is not PENDING")
    void accept_fromAccepted_shouldThrowException() {
      // Given
      Friendship friendship =
          Friendship.builder()
              .requester(requester)
              .addressee(addressee)
              .status(FriendshipStatus.ACCEPTED)
              .build();

      // When/Then
      assertThatThrownBy(() -> friendship.accept())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot accept friendship")
          .hasMessageContaining("ACCEPTED")
          .hasMessageContaining("expected PENDING");
    }

    @Test
    @DisplayName("accept should throw exception when status is REJECTED")
    void accept_fromRejected_shouldThrowException() {
      // Given
      Friendship friendship =
          Friendship.builder()
              .requester(requester)
              .addressee(addressee)
              .status(FriendshipStatus.REJECTED)
              .build();

      // When/Then
      assertThatThrownBy(() -> friendship.accept())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot accept friendship")
          .hasMessageContaining("REJECTED");
    }

    @Test
    @DisplayName("reject should change status from PENDING to REJECTED")
    void reject_fromPending_shouldChangeToRejected() {
      // Given
      Friendship friendship = Friendship.createRequest(requester, addressee);

      // When
      friendship.reject();

      // Then
      assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
    }

    @Test
    @DisplayName("reject should throw exception when status is not PENDING")
    void reject_fromAccepted_shouldThrowException() {
      // Given
      Friendship friendship =
          Friendship.builder()
              .requester(requester)
              .addressee(addressee)
              .status(FriendshipStatus.ACCEPTED)
              .build();

      // When/Then
      assertThatThrownBy(() -> friendship.reject())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot reject friendship")
          .hasMessageContaining("ACCEPTED")
          .hasMessageContaining("expected PENDING");
    }

    @Test
    @DisplayName("block should change status to BLOCKED from any state")
    void block_fromAnyState_shouldChangeToBlocked() {
      // Given PENDING
      Friendship pendingFriendship = Friendship.createRequest(requester, addressee);
      pendingFriendship.block();
      assertThat(pendingFriendship.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);

      // Given ACCEPTED
      Friendship acceptedFriendship =
          Friendship.builder()
              .requester(requester)
              .addressee(addressee)
              .status(FriendshipStatus.ACCEPTED)
              .build();
      acceptedFriendship.block();
      assertThat(acceptedFriendship.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
    }
  }

  @Nested
  @DisplayName("Status Check Methods Tests")
  class StatusCheckTests {

    @Test
    @DisplayName("isActive should return true only for ACCEPTED status")
    void isActive_shouldReturnTrueOnlyForAccepted() {
      Friendship pending = Friendship.builder().status(FriendshipStatus.PENDING).build();
      Friendship accepted = Friendship.builder().status(FriendshipStatus.ACCEPTED).build();
      Friendship rejected = Friendship.builder().status(FriendshipStatus.REJECTED).build();
      Friendship blocked = Friendship.builder().status(FriendshipStatus.BLOCKED).build();

      assertThat(pending.isActive()).isFalse();
      assertThat(accepted.isActive()).isTrue();
      assertThat(rejected.isActive()).isFalse();
      assertThat(blocked.isActive()).isFalse();
    }

    @Test
    @DisplayName("isPending should return true only for PENDING status")
    void isPending_shouldReturnTrueOnlyForPending() {
      Friendship pending = Friendship.builder().status(FriendshipStatus.PENDING).build();
      Friendship accepted = Friendship.builder().status(FriendshipStatus.ACCEPTED).build();
      Friendship rejected = Friendship.builder().status(FriendshipStatus.REJECTED).build();

      assertThat(pending.isPending()).isTrue();
      assertThat(accepted.isPending()).isFalse();
      assertThat(rejected.isPending()).isFalse();
    }
  }

  @Nested
  @DisplayName("User Involvement Tests")
  class UserInvolvementTests {

    @Test
    @DisplayName("isRequester should return true for requester's ID")
    void isRequester_withRequesterId_shouldReturnTrue() {
      // Given
      Friendship friendship = Friendship.createRequest(requester, addressee);

      // When/Then
      assertThat(friendship.isRequester(requester.getId())).isTrue();
      assertThat(friendship.isRequester(addressee.getId())).isFalse();
      assertThat(friendship.isRequester(999L)).isFalse();
    }

    @Test
    @DisplayName("isAddressee should return true for addressee's ID")
    void isAddressee_withAddresseeId_shouldReturnTrue() {
      // Given
      Friendship friendship = Friendship.createRequest(requester, addressee);

      // When/Then
      assertThat(friendship.isAddressee(addressee.getId())).isTrue();
      assertThat(friendship.isAddressee(requester.getId())).isFalse();
      assertThat(friendship.isAddressee(999L)).isFalse();
    }

    @Test
    @DisplayName("involvesUser should return true for both requester and addressee")
    void involvesUser_withInvolvedUserId_shouldReturnTrue() {
      // Given
      Friendship friendship = Friendship.createRequest(requester, addressee);

      // When/Then
      assertThat(friendship.involvesUser(requester.getId())).isTrue();
      assertThat(friendship.involvesUser(addressee.getId())).isTrue();
      assertThat(friendship.involvesUser(999L)).isFalse();
    }

    @Test
    @DisplayName("isRequester should return false when requester is null")
    void isRequester_withNullRequester_shouldReturnFalse() {
      // Given
      Friendship friendship =
          Friendship.builder().addressee(addressee).status(FriendshipStatus.PENDING).build();

      // When/Then
      assertThat(friendship.isRequester(1L)).isFalse();
    }

    @Test
    @DisplayName("isAddressee should return false when addressee is null")
    void isAddressee_withNullAddressee_shouldReturnFalse() {
      // Given
      Friendship friendship =
          Friendship.builder().requester(requester).status(FriendshipStatus.PENDING).build();

      // When/Then
      assertThat(friendship.isAddressee(1L)).isFalse();
    }
  }

  @Nested
  @DisplayName("Lombok Generated Methods Tests")
  class LombokTests {

    @Test
    @DisplayName("NoArgsConstructor should create empty friendship")
    void noArgsConstructor_shouldCreateEmptyFriendship() {
      // When
      Friendship friendship = new Friendship();

      // Then
      assertThat(friendship.getId()).isNull();
      assertThat(friendship.getRequester()).isNull();
      assertThat(friendship.getAddressee()).isNull();
      assertThat(friendship.getStatus()).isNull();
    }

    @Test
    @DisplayName("AllArgsConstructor should set all fields")
    void allArgsConstructor_shouldSetAllFields() {
      // Given
      Long id = 1L;
      FriendshipStatus status = FriendshipStatus.PENDING;

      // When
      Friendship friendship = new Friendship(id, requester, addressee, status, null, null);

      // Then
      assertThat(friendship.getId()).isEqualTo(id);
      assertThat(friendship.getRequester()).isEqualTo(requester);
      assertThat(friendship.getAddressee()).isEqualTo(addressee);
      assertThat(friendship.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("Setters should update fields")
    void setters_shouldUpdateFields() {
      // Given
      Friendship friendship = new Friendship();

      // When
      friendship.setId(1L);
      friendship.setRequester(requester);
      friendship.setAddressee(addressee);
      friendship.setStatus(FriendshipStatus.ACCEPTED);

      // Then
      assertThat(friendship.getId()).isEqualTo(1L);
      assertThat(friendship.getRequester()).isEqualTo(requester);
      assertThat(friendship.getAddressee()).isEqualTo(addressee);
      assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }
  }
}
