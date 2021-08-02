package com.anpopo.social.account;

import com.anpopo.social.account.domain.Account;
import com.anpopo.social.account.form.SignUpForm;
import com.anpopo.social.account.repository.AccountRepository;
import com.anpopo.social.follow.Follow;
import com.anpopo.social.follow.FollowRepository;
import com.anpopo.social.interest.Interest;
import com.anpopo.social.settings.form.NotificationForm;
import com.anpopo.social.settings.form.ProfileForm;
import com.anpopo.social.tag.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class AccountService implements UserDetailsService {

    private final AccountRepository accountRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final FollowRepository followRepository;

    public void signUpProcess(SignUpForm signUpForm) {

        // 회원 저장
        Account newAccount = createAndSaveUserAccount(signUpForm);

        // 메일 발송
        sendMail(newAccount);
        
        // 로그인 처리
        login(newAccount);
    }

    public void login(Account account) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                new UserAccount(account),  // spring security 에서 참조하는 principal
                account.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        //스프링 스큐리티 관점에서 로그인
        //- SecurityContext 에 Authentication(Token)이 존재하는가?
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    public void sendMail(Account account) {
        SimpleMailMessage mailMessage = getSimpleMailMessage(
                account,
                "The Social, 회원 가입 인증",
                "/email-check-token?token=");
        mailSender.send(mailMessage);
    }

    public void completeSignUp(Account findAccount) {
        // 회원가입 완료 처리 - 이메일 인증값 true, 회원 가입 시간
        findAccount.completeSignUp();

        // 로그인 처리
        login(findAccount);
    }

    public void sendEmailLoginLink(Account account) {
        account.generateEmailCheckToken();

        SimpleMailMessage mailMessage = getSimpleMailMessage(
                account,
                "스터디올래, 로그인 링크",
                "/login-by-email?token=");

        mailSender.send(mailMessage);
    }

    public void updateProfile(Account account, ProfileForm profileForm) {
        account.updateProfile(profileForm);
        accountRepository.save(account);
    }

    public void updatePassword(Account account, String newPassword) {
        account.updatePassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
    }

    public void updateNotifications(Account account, NotificationForm notificationForm) {
        account.updateNotifications(notificationForm);
        accountRepository.save(account);
    }

    private Account createAndSaveUserAccount(SignUpForm signUpForm) {
        // builder 패턴 사용시 기본값이 null 로 들어감.
        // 생성자를 통해 하는것을 좀더 어케 좀 해보자구요@@@@@
        Account newAccount = new Account();
        newAccount.createNewAccount(
                signUpForm.getEmail(),
                signUpForm.getNickname(),
                passwordEncoder.encode(signUpForm.getPassword())
        );

        newAccount.generateEmailCheckToken();

        accountRepository.save(newAccount);
        return newAccount;
    }

    private SimpleMailMessage getSimpleMailMessage(Account account, String subject, String url) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(account.getEmail());
        mailMessage.setSubject(subject);
        mailMessage.setText(url + account.getEmailCheckToken() + "&email=" + account.getEmail());
        return mailMessage;
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String emailOrNickname) throws UsernameNotFoundException {

        Account account = accountRepository.findByEmail(emailOrNickname);

        if(account == null) {
            account = accountRepository.findByNickname(emailOrNickname);
        }

        if (account == null) {
            throw new UsernameNotFoundException(emailOrNickname + "로 등록된 정보를 찾을 수 없습니다.");
        }

        return new UserAccount(account);
    }

    public void updateAccountInfo(Account account, String nickname) {
        account.updateAccountInfo(nickname);
        accountRepository.save(account);
        login(account);

    }

    public Set<Interest> getInterest(Account account) {
        Optional<Account> findAccount = accountRepository.findAccountWithInterestById(account.getId());
        return findAccount.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 계정 정보 입니다.")).getInterests();
    }

    public void addInterest(Account account, Interest interest) {
        Optional<Account> findAccount = accountRepository.findById(account.getId());
        findAccount.ifPresent(a -> a.getInterests().add(interest));
    }

    public void removeInterest(Account account, Interest interest) {
        Optional<Account> findAccount = accountRepository.findById(account.getId());
        findAccount.ifPresent(a -> a.getInterests().remove(interest));
    }

    /**
     * findAccount -> 팔로우 할 계정 -> followed
     * account -> 팔로우를 신청한 계정 -> follow
     */
    public void followRequest(Account findAccount, Account requestAccount) {
        
        // follow 신청을 받는 계정과 팔로우 신청을 한 계정으로 등록된 팔로우 객체가 없는 경우
        if (!followRepository.existsFollowByFollowedAndFollow(findAccount, requestAccount)) {
            // 새로운 follow 객체를 만들어 준다.
            Follow follow = followRepository.save( new Follow(findAccount, requestAccount));

            // TODO findAccount 에 알람 보내주기
            findAccount.addFollowers(follow);
            requestAccount.addFollowing(follow);
        }
    }

    public void followAccept(Account followAccount, Account requestAccount) {
        Follow follow = followRepository.findFollowByFollowedAndFollow(followAccount, requestAccount);

        if (follow != null) {
            follow.acceptFollowRequest();
        }
    }

    public void followReject(Account followAccount, Account requestAccount) {
        Follow follow = followRepository.findFollowByFollowedAndFollow(followAccount, requestAccount);

        if (follow != null) {
            followAccount.getFollowers().remove(follow);
            requestAccount.getFollowing().remove(follow);
            followRepository.delete(follow);
        }
    }
}
