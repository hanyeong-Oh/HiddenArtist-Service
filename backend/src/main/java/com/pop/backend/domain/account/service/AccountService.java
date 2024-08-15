package com.pop.backend.domain.account.service;

import com.pop.backend.domain.account.controller.request.AccountDeleteFollowArtistRequest.FollowArtistToken;
import com.pop.backend.domain.account.controller.response.AccountGetDetailResponse;
import com.pop.backend.domain.account.controller.response.AccountGetSimpleResponse;
import com.pop.backend.domain.account.controller.response.FollowArtistGetListResponse;
import com.pop.backend.domain.account.persistence.Account;
import com.pop.backend.domain.account.persistence.repository.AccountRepository;
import com.pop.backend.domain.artist.persistence.Artist;
import com.pop.backend.domain.artist.persistence.repository.ArtistRepository;
import com.pop.backend.global.exception.type.EntityException;
import com.pop.backend.global.exception.type.SecurityException;
import com.pop.backend.global.exception.type.ServiceErrorCode;
import com.pop.backend.global.jwt.TokenService;
import com.pop.backend.global.security.service.OAuth2UnlinkManager;
import com.pop.backend.global.type.EntityToken;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accountRepository;
  private final ArtistRepository artistRepository;
  private final TokenService tokenService;
  private final OAuth2UnlinkManager unlinkManager;

  @Transactional
  public void withDrawAccount(String email, String refreshToken) {
    Account account = findAccountByEmail(email);
    validateAccountStatus(account);
    unlinkAccount(account);
    tokenService.removeRefreshToken(refreshToken);
    account.updateDeleteDate();
    accountRepository.save(account);
  }

  @Transactional(readOnly = true)
  public AccountGetSimpleResponse getAccountSimpleInfo(String email) {
    Account account = findAccountByEmail(email);
    return AccountGetSimpleResponse.of(account);
  }

  @Transactional(readOnly = true)
  public AccountGetDetailResponse getAccountDetailInfo(String email) {
    Account account = findAccountByEmail(email);
    return AccountGetDetailResponse.of(account);
  }

  @Transactional
  public void updateAccountNickname(String email, String nickname) {
    Account account = findAccountByEmail(email);
    account.updateNickname(nickname);
    accountRepository.save(account);
  }

  @Transactional
  public void updateAccountImage(String email, String profileImage) {
    Account account = findAccountByEmail(email);
    // s3 연동이 필요.
    // 들어온 이미지 파일을 -> s3에 업로드
    // s3에서 응답되는 파일을 저장
    account.updateProfileImage(profileImage);
    accountRepository.save(account);
  }

  @Transactional(readOnly = true)
  public FollowArtistGetListResponse getFollowArtists(String email) {
    List<Artist> artists = artistRepository.findFollowArtistListByAccountEmail(email);
    return FollowArtistGetListResponse.convert(artists);
  }

  @Transactional
  public void deleteFollowArtists(String email, List<FollowArtistToken> artistTokens) {
    List<String> tokens = artistTokens.stream()
                                      .map(FollowArtistToken::token)
                                      .map(EntityToken.ARTIST::identifyToken)
                                      .toList();
    accountRepository.removeFollowArtists(email, tokens);
  }

  private Account findAccountByEmail(String email) {
    return accountRepository.findByEmail(email)
                            .orElseThrow(() -> new EntityException(ServiceErrorCode.USER_NOT_FOUND));
  }

  private void validateAccountStatus(Account account) {
    if (account.isDeleted()) {
      throw new EntityException(ServiceErrorCode.ACCOUNT_ALREADY_DELETED);
    }
  }

  private void unlinkAccount(Account account) {
    try {
      if (!unlinkManager.unlink(account.getProviderType(), String.valueOf(account.getProviderId()))) {
        throw new SecurityException(ServiceErrorCode.UNLINK_FAIL);
      }
    } catch (Exception e) {
      throw new SecurityException(ServiceErrorCode.UNLINK_FAIL);
    }
  }

}