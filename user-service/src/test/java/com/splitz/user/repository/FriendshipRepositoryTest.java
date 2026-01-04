package com.splitz.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.user.config.JpaAuditingConfig;
import com.splitz.user.model.Friendship;
import com.splitz.user.model.FriendshipStatus;
import com.splitz.user.model.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for FriendshipRepository. Tests custom query methods and repository operations.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("FriendshipRepository Integration Tests")
class FriendshipRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private FriendshipRepository friendshipRepository;

  private User alice;
  private User bob;
  private User charlie;

  @BeforeEach
  void setUp() {
    // Create test users
    alice = createAndPersistUser("alice", "alice@test.com", "Alice");
    bob = createAndPersistUser("bob", "bob@test.com", "Bob");
    charlie = createAndPersistUser("charlie", "charlie@test.com", "Charlie");

    entityManager.flush();
    entityManager.clear();
  }

  private User createAndPersistUser(String username, String email, String firstName) {
    User user = new User();
    user.setUsername(username);
    user.setEmail(email);
    user.setFirstName(firstName);
    user.setPassword("password123");
    user.setEnabled(true);
    return entityManager.persistAndFlush(user);
  }

  private Friendship createAndPersistFriendship(
      User requester, User addressee, FriendshipStatus status) {
    Friendship friendship =
        Friendship.builder().requester(requester).addressee(addressee).status(status).build();
    return entityManager.persistAndFlush(friendship);
  }

  @Nested
  @DisplayName("Basic CRUD Operations")
  class CrudTests {

    @Test
    @DisplayName("should save and retrieve friendship")
    void save_shouldPersistFriendship() {
      // Given
      Friendship friendship = Friendship.createRequest(alice, bob);

      // When
      Friendship saved = friendshipRepository.save(friendship);
      entityManager.flush();
      entityManager.clear();

      // Then
      Optional<Friendship> found = friendshipRepository.findById(saved.getId());
      assertThat(found).isPresent();
      assertThat(found.get().getRequester().getId()).isEqualTo(alice.getId());
      assertThat(found.get().getAddressee().getId()).isEqualTo(bob.getId());
      assertThat(found.get().getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    @DisplayName("should update friendship status")
    void update_shouldChangeStatus() {
      // Given
      Friendship friendship = createAndPersistFriendship(alice, bob, FriendshipStatus.PENDING);
      entityManager.clear();

      // When
      Friendship toUpdate = friendshipRepository.findById(friendship.getId()).orElseThrow();
      toUpdate.accept();
      friendshipRepository.save(toUpdate);
      entityManager.flush();
      entityManager.clear();

      // Then
      Friendship updated = friendshipRepository.findById(friendship.getId()).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    @DisplayName("should delete friendship")
    void delete_shouldRemoveFriendship() {
      // Given
      Friendship friendship = createAndPersistFriendship(alice, bob, FriendshipStatus.ACCEPTED);
      Long friendshipId = friendship.getId();
      entityManager.clear();

      // When
      friendshipRepository.deleteById(friendshipId);
      entityManager.flush();

      // Then
      assertThat(friendshipRepository.findById(friendshipId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByRequesterOrAddressee Tests")
  class FindByRequesterOrAddresseeTests {

    @Test
    @DisplayName("should find all friendships where user is requester or addressee")
    void shouldFindAllFriendshipsForUser() {
      // Given
      createAndPersistFriendship(alice, bob, FriendshipStatus.ACCEPTED); // Alice is requester
      createAndPersistFriendship(charlie, alice, FriendshipStatus.PENDING); // Alice is addressee
      createAndPersistFriendship(bob, charlie, FriendshipStatus.ACCEPTED); // Alice not involved
      entityManager.clear();

      // When
      List<Friendship> aliceFriendships =
          friendshipRepository.findByRequesterOrAddressee(alice, alice);

      // Then
      assertThat(aliceFriendships).hasSize(2);
    }
  }

  @Nested
  @DisplayName("findByUserAndStatus Tests")
  class FindByUserAndStatusTests {

    @Test
    @DisplayName("should find friendships by user and status")
    void shouldFindByUserAndStatus() {
      // Given
      createAndPersistFriendship(alice, bob, FriendshipStatus.ACCEPTED);
      createAndPersistFriendship(alice, charlie, FriendshipStatus.PENDING);
      createAndPersistFriendship(bob, alice, FriendshipStatus.ACCEPTED);
      entityManager.clear();

      // When
      List<Friendship> acceptedFriendships =
          friendshipRepository.findByUserAndStatus(alice, FriendshipStatus.ACCEPTED);

      // Then
      assertThat(acceptedFriendships).hasSize(2);
    }
  }

  @Nested
  @DisplayName("findByAddresseeAndStatus Tests")
  class FindByAddresseeAndStatusTests {

    @Test
    @DisplayName("should find pending requests received by user")
    void shouldFindPendingRequestsReceivedByUser() {
      // Given
      createAndPersistFriendship(bob, alice, FriendshipStatus.PENDING); // Alice receives from Bob
      createAndPersistFriendship(
          charlie, alice, FriendshipStatus.PENDING); // Alice receives from Charlie
      createAndPersistFriendship(
          alice, bob, FriendshipStatus.PENDING); // Alice sends to Bob (not counted)
      entityManager.clear();

      // When
      List<Friendship> pendingRequests =
          friendshipRepository.findByAddresseeAndStatus(alice, FriendshipStatus.PENDING);

      // Then
      assertThat(pendingRequests).hasSize(2);
    }

    @Test
    @DisplayName("should return paginated results")
    void shouldReturnPaginatedResults() {
      // Given
      createAndPersistFriendship(bob, alice, FriendshipStatus.PENDING);
      createAndPersistFriendship(charlie, alice, FriendshipStatus.PENDING);
      entityManager.clear();

      // When
      Page<Friendship> page =
          friendshipRepository.findByAddresseeAndStatus(
              alice, FriendshipStatus.PENDING, PageRequest.of(0, 1));

      // Then
      assertThat(page.getTotalElements()).isEqualTo(2);
      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getTotalPages()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("findByRequesterAndStatus Tests")
  class FindByRequesterAndStatusTests {

    @Test
    @DisplayName("should find pending requests sent by user")
    void shouldFindPendingRequestsSentByUser() {
      // Given
      createAndPersistFriendship(alice, bob, FriendshipStatus.PENDING); // Alice sends to Bob
      createAndPersistFriendship(
          alice, charlie, FriendshipStatus.PENDING); // Alice sends to Charlie
      createAndPersistFriendship(
          bob, alice, FriendshipStatus.PENDING); // Alice receives (not counted)
      entityManager.clear();

      // When
      List<Friendship> sentRequests =
          friendshipRepository.findByRequesterAndStatus(alice, FriendshipStatus.PENDING);

      // Then
      assertThat(sentRequests).hasSize(2);
    }
  }

  @Nested
  @DisplayName("findAcceptedFriendships Tests")
  class FindAcceptedFriendshipsTests {

    @Test
    @DisplayName("should find all accepted friendships for user")
    void shouldFindAcceptedFriendships() {
      // Given
      createAndPersistFriendship(alice, bob, FriendshipStatus.ACCEPTED);
      createAndPersistFriendship(charlie, alice, FriendshipStatus.ACCEPTED);
      createAndPersistFriendship(alice, charlie, FriendshipStatus.PENDING); // Not accepted
      entityManager.clear();

      // When
      List<Friendship> acceptedFriendships = friendshipRepository.findAcceptedFriendships(alice);

      // Then
      assertThat(acceptedFriendships).hasSize(2);
    }

    @Test
    @DisplayName("should return paginated accepted friendships")
    void shouldReturnPaginatedAcceptedFriendships() {
      // Given
      createAndPersistFriendship(alice, bob, FriendshipStatus.ACCEPTED);
      createAndPersistFriendship(charlie, alice, FriendshipStatus.ACCEPTED);
      entityManager.clear();

      // When
      Page<Friendship> page =
          friendshipRepository.findAcceptedFriendships(alice, PageRequest.of(0, 1));

      // Then
      assertThat(page.getTotalElements()).isEqualTo(2);
      assertThat(page.getContent()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("existsBetweenUsers Tests")
  class ExistsBetweenUsersTests {

    @Test
    @DisplayName("should return true when friendship exists in either direction")
    void shouldReturnTrueWhenFriendshipExists() {
      // Given
      createAndPersistFriendship(alice, bob, FriendshipStatus.PENDING);
      entityManager.clear();

      // When/Then
      assertThat(friendshipRepository.existsBetweenUsers(alice, bob)).isTrue();
      assertThat(friendshipRepository.existsBetweenUsers(bob, alice)).isTrue(); // Reverse order
    }

    @Test
    @DisplayName("should return false when no friendship exists")
    void shouldReturnFalseWhenNoFriendshipExists() {
      // Given - no friendships created
      entityManager.clear();

      // When/Then
      assertThat(friendshipRepository.existsBetweenUsers(alice, bob)).isFalse();
    }
  }

  @Nested
  @DisplayName("findBetweenUsers Tests")
  class FindBetweenUsersTests {

    @Test
    @DisplayName("should find friendship between users regardless of direction")
    void shouldFindFriendshipBetweenUsers() {
      // Given
      Friendship friendship = createAndPersistFriendship(alice, bob, FriendshipStatus.ACCEPTED);
      entityManager.clear();

      // When
      Optional<Friendship> found1 = friendshipRepository.findBetweenUsers(alice, bob);
      Optional<Friendship> found2 = friendshipRepository.findBetweenUsers(bob, alice); // Reverse

      // Then
      assertThat(found1).isPresent();
      assertThat(found2).isPresent();
      assertThat(found1.get().getId()).isEqualTo(friendship.getId());
      assertThat(found2.get().getId()).isEqualTo(friendship.getId());
    }

    @Test
    @DisplayName("should return empty when no friendship exists")
    void shouldReturnEmptyWhenNoFriendship() {
      // When
      Optional<Friendship> found = friendshipRepository.findBetweenUsers(alice, bob);

      // Then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByRequesterAndAddressee Tests")
  class FindByRequesterAndAddresseeTests {

    @Test
    @DisplayName("should find friendship by exact requester and addressee")
    void shouldFindByExactRequesterAndAddressee() {
      // Given
      createAndPersistFriendship(alice, bob, FriendshipStatus.PENDING);
      entityManager.clear();

      // When
      Optional<Friendship> found = friendshipRepository.findByRequesterAndAddressee(alice, bob);
      Optional<Friendship> notFound = friendshipRepository.findByRequesterAndAddressee(bob, alice);

      // Then
      assertThat(found).isPresent();
      assertThat(notFound).isEmpty(); // Direction matters here
    }
  }

  @Nested
  @DisplayName("Count Methods Tests")
  class CountMethodsTests {

    @Test
    @DisplayName("should count pending requests for addressee")
    void shouldCountPendingRequests() {
      // Given
      createAndPersistFriendship(bob, alice, FriendshipStatus.PENDING);
      createAndPersistFriendship(charlie, alice, FriendshipStatus.PENDING);
      createAndPersistFriendship(alice, bob, FriendshipStatus.PENDING); // Not counted
      entityManager.clear();

      // When
      long count = friendshipRepository.countByAddresseeAndStatus(alice, FriendshipStatus.PENDING);

      // Then
      assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("should count accepted friendships for user")
    void shouldCountAcceptedFriendships() {
      // Given
      createAndPersistFriendship(alice, bob, FriendshipStatus.ACCEPTED);
      createAndPersistFriendship(charlie, alice, FriendshipStatus.ACCEPTED);
      createAndPersistFriendship(alice, charlie, FriendshipStatus.PENDING); // Not counted
      entityManager.clear();

      // When
      long count = friendshipRepository.countAcceptedFriendships(alice);

      // Then
      assertThat(count).isEqualTo(2);
    }
  }
}
