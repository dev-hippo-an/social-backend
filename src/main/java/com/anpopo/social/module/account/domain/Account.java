package com.anpopo.social.module.account.domain;

import com.anpopo.social.module.interest.Interest;
import com.anpopo.social.module.settings.form.NotificationForm;
import com.anpopo.social.module.settings.form.ProfileForm;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import javax.persistence.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@NamedEntityGraph(name = "Account.withInterest", attributeNodes = {
        @NamedAttributeNode("interests")
})

@NamedEntityGraph(name = "Account.withFollowers", attributeNodes = {
        @NamedAttributeNode("followers")
})

@NamedEntityGraph(name = "Account.withFollowing", attributeNodes = {
        @NamedAttributeNode("following")
})

@NamedEntityGraph(
        name = "Account.withFollowersAndFollowersAccount",
        attributeNodes = {@NamedAttributeNode(value = "followers", subgraph = "follow")},
        subgraphs = @NamedSubgraph(name = "follow", attributeNodes = @NamedAttributeNode("follow"))
)
@NamedEntityGraph(
        name = "Account.withFollowingAndFollowingAccount",
        attributeNodes = {@NamedAttributeNode(value = "following", subgraph = "followed")},
        subgraphs = @NamedSubgraph(name = "followed", attributeNodes = @NamedAttributeNode("followed"))
)

@NamedEntityGraph(
        name = "Account.withFollowersAndFollowingAndAccount",
        attributeNodes = {
                @NamedAttributeNode(value = "followers", subgraph = "follow"),
                @NamedAttributeNode(value = "following", subgraph = "followed"),
        },
        subgraphs = {
                @NamedSubgraph(name = "follow", attributeNodes = @NamedAttributeNode("follow")),
                @NamedSubgraph(name = "followed", attributeNodes = @NamedAttributeNode("followed"))
        }
)


@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@SQLDelete(sql = "UPDATE account SET deleted = true WHERE id=?")
@Where(clause = "deleted=false")
@Entity
public class Account {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String nickname;

    @Column(length = 1000)
    private String password;

    private boolean emailVerified = false;

    private String emailCheckToken;

    private LocalDateTime emailCheckTokenGeneratedAt;

    private boolean cellPhoneVerified = false;

    private String cellPhoneCheckToken;

    private LocalDateTime cellPhoneCheckTokenGeneratedAt;

    private LocalDateTime joinedAt;

    private String bio;

    @Lob @Basic(fetch = FetchType.EAGER)
    private String profileImage;

    private boolean deleted = false;

    @ManyToMany(fetch = FetchType.LAZY)
    private Set<Interest> interests = new HashSet<>();

    // 팔로잉 한 계정의 포스팅이 올라온 경우의 알람 설정
    private boolean followingAccountPostingByWeb = false;

    // 관심 있는 주제로 포스팅이 올라온 경우 알람 설정
    private boolean interestSubjectPostingByWeb = false;

    @OneToMany(mappedBy = "followed", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Follow> followers = new HashSet<>();

    @OneToMany(mappedBy = "follow", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Follow> following = new HashSet<>();

    public void generateEmailCheckToken() {
        this.emailCheckToken = UUID.randomUUID().toString();
    }

    public boolean isValidToken(String token) {
        return this.emailCheckToken.equals(token);
    }

    public void completeSignUp() {
        this.emailVerified = true;
        this.joinedAt = LocalDateTime.now();
    }

    public boolean canSendConfirmMail() {
        // 제일 처음 재전송 메일을 보내는 경우
        if(this.emailCheckTokenGeneratedAt == null) {
            this.emailCheckTokenGeneratedAt = LocalDateTime.now();
            return true;
        }
        // 재전송 메일을 두번째 이상 보내는 경우 -> 시간 확인이 필요
        else {
            // 1시간 안에 보낸 메일인가?
            if (this.emailCheckTokenGeneratedAt.isBefore(LocalDateTime.now().minusHours(1))){
                this.emailCheckTokenGeneratedAt = LocalDateTime.now();
                return true;
            }
            return false;
        }
    }

    public void updateProfile(ProfileForm profileForm) {
        this.bio = profileForm.getBio();
        this.profileImage = profileForm.getProfileImage();
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateNotifications(NotificationForm notificationForm) {
        this.followingAccountPostingByWeb = notificationForm.isFollowingAccountPostingByWeb();
        this.interestSubjectPostingByWeb = notificationForm.isInterestSubjectPostingByWeb();
    }

    public void updateAccountInfo(String nickname) {
        this.nickname = nickname;
    }

    public String getEncodedNickname() {
        return URLEncoder.encode(this.nickname, StandardCharsets.UTF_8);
    }

    public void createNewAccount(String email, String nickname, String password) {
        this.email = email;
        this.nickname = nickname;
        this.password = password;
    }

    public void addFollowers(Follow follow) {
        this.followers.add(follow);
    }

    public void addFollowing(Follow follow) {
        this.following.add(follow);
    }


    public void removeFollowers(Follow follow) {
        this.followers.remove(follow);
    }

    public void removeFollowing(Follow follow) {
        this.following.remove(follow);
    }

    public void deleteFollowers(Follow follow) {
        this.followers.remove(follow);
    }

    public void deleteFollowing(Follow follow) {
        this.following.remove(follow);
    }
}
