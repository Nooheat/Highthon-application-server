package com.highthon.highthon3server.service.application;

import com.highthon.highthon3server.domain.application.*;
import com.highthon.highthon3server.dto.application.ApplicationSaveDto;
import com.highthon.highthon3server.dto.application.GetApplicationConditionDto;
import com.highthon.highthon3server.dto.application.SaveResponse;
import com.highthon.highthon3server.exception.ApplicationNotFoundException;
import com.highthon.highthon3server.exception.AuthenticationException;
import com.highthon.highthon3server.exception.DuplicatedValueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    @Autowired
    private ApplicationRepository applicationRepository;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private EntityManager em;

    @Value("${limit.life-develop}")
    private Integer LIFE_DEVELOP_LIMIT;

    @Value("${limit.life-design}")
    private Integer LIFE_DESIGN_LIMIT;

    @Value("${limit.game-develop}")
    private Integer GAME_DEVELOP_LIMIT;

    @Value("${limit.game-design}")
    private Integer GAME_DESIGN_LIMIT;

    final List<String> SORTABLE_APPLICATION_FIELDS = Arrays.asList("applicationId", "name", "email", "sex", "area", "position", "phone", "belong", "createdDate");

    @Transactional
    public void saveApplication(ApplicationSaveDto dto) {
        Integer waitingNumber = null;
        if (applicationRepository.existsByPhone(dto.getPhone()))
            throw new DuplicatedValueException("전화번호가 중복되었습니다. 다시 한 번 확인해주세요.");
        if (applicationRepository.existsByEmail(dto.getEmail()))
            throw new DuplicatedValueException("이메일이 중복되었습니다. 다시 한 번 확인해주세요.");

        int limit = getLimit(dto.getArea(), dto.getPosition());
        int count = applicationRepository.countByAreaAndPosition(dto.getArea(), dto.getPosition());

        Application application = dto.toEntity();
        application.setPassword(passwordEncoder.encode(application.getPassword()));
        application.setBelong(application.getBelong().replace(" ", ""));

//        application.setIsAccepted(count < limit);

        applicationRepository.save(application);

//        if (!application.getIsAccepted()) waitingNumber = applicationRepository.getWaitingNumber(application);
//
//        return new SaveResponse(application.getIsAccepted(), waitingNumber);
    }

    private int getLimit(Area area, Position position) {
        if (area == Area.GAME) {
            if (position == Position.DESIGN) return GAME_DESIGN_LIMIT;
            else return GAME_DEVELOP_LIMIT;
        } else {
            if (position == Position.DESIGN) return LIFE_DESIGN_LIMIT;
            else return LIFE_DEVELOP_LIMIT;
        }
    }


    @Transactional(readOnly = true)
    public ApplicationCondition getApplicationCondition(GetApplicationConditionDto dto) {
        Application application = applicationRepository.findByEmail(dto.getEmail()).orElse(null);
        if (application == null) throw new ApplicationNotFoundException();
        if (!passwordEncoder.matches(dto.getPassword(), application.getPassword()))
            throw new AuthenticationException("비밀번호가 일치하지 않습니다.");

        if (!application.getIsAccepted()) {
            return new ApplicationCondition(application.getName(), false, null);
        }

        Long count = applicationRepository.countEarlierAcceptedApplicationsByAreaAndPosition(application);

        if (count <= getLimit(application.getArea(), application.getPosition())) {
            return new ApplicationCondition(application.getName(), true, null);
        }

        return new ApplicationCondition(application.getName(), true, count - getLimit(application.getArea(), application.getPosition()));
    }

    @Transactional(readOnly = true)
    public List<Application> getAcceptedApplications(Pageable pageable) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Application> q = cb.createQuery(Application.class);
        Root root = q.from(Application.class);

        final int page = pageable.getPageNumber();
        final int size = pageable.getPageSize();
        final List<Order> orders = getSortableOrderList(SORTABLE_APPLICATION_FIELDS, pageable.getSort(), root);

        q.orderBy(orders);
        TypedQuery<Application> query = em.createQuery(q);

        query.setFirstResult(page * size);
        query.setMaxResults(size);

        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationIncludesWaitingNumber> getWaitingApplications(@PageableDefault(sort = "applicationId,desc") Pageable pageable) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ApplicationIncludesWaitingNumber> q = cb.createQuery(ApplicationIncludesWaitingNumber.class);
        Root<Application> root = q.from(Application.class);

        final int page = pageable.getPageNumber();
        final int size = pageable.getPageSize();
        final List<Order> orders = getSortableOrderList(SORTABLE_APPLICATION_FIELDS, pageable.getSort(), root);

        Subquery<Long> sq = q.subquery(Long.class);
        Root<Application> subRoot = sq.from(Application.class);

        sq.select(cb.count(subRoot.get("applicationId")));
        sq.where(cb.and(
                cb.isFalse(subRoot.get("isAccepted")),
                cb.lessThanOrEqualTo(subRoot.get("createdDate"), root.get("createdDate")),
                cb.equal(subRoot.get("area"), root.get("area")),
                cb.equal(subRoot.get("position"), root.get("position"))));

        q.select(cb.construct(ApplicationIncludesWaitingNumber.class,
                root.get("applicationId"),
                root.get("name"),
                root.get("email"),
                root.get("sex"),
                root.get("area"),
                root.get("position"),
                root.get("phone"),
                root.get("belong"),
                root.get("isAccepted"),
                root.get("createdDate"),
                sq.getSelection()))
                .where(cb.isFalse(root.get("isAccepted")))
                .orderBy(orders);

        TypedQuery<ApplicationIncludesWaitingNumber> query = em.createQuery(q);

        query.setFirstResult(page * size);
        query.setMaxResults(size);

        return query.getResultList();
    }

    private List<Order> getSortableOrderList(List<String> sortableFields, Sort sort, Root root) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        return sort.stream().filter(sortElement -> sortableFields.contains(sortElement.getProperty()))
                .map(sortElement -> {
                    if (sortElement.isAscending()) return cb.asc(root.get(sortElement.getProperty()));
                    else return cb.desc(root.get(sortElement.getProperty()));

                    // org.springframework.data.domain.Sort는 Direction 값 Default로 Direction.ASC를 가짐
                    // org.springframework.data.domain.Direction 은 Enum 클래스라 Direction.ASC 아니면 Direction.DESC임. 다른 케이스 없음
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteApplicationAndUpdateLatestWaitingApplicationToAccepted(Long applicationId) {
        Application originApplication = applicationRepository.findById(applicationId).orElse(null);

        if (originApplication == null) {
            throw new ApplicationNotFoundException(applicationId);
        }

        applicationRepository.delete(originApplication);

        if (!originApplication.getIsAccepted()) return;

        Application latestWaitingApplication = applicationRepository.findFirstByAreaAndPositionAndIsAcceptedIsFalseOrderByCreatedDate(originApplication.getArea(), originApplication.getPosition()).orElse(null);

        if (latestWaitingApplication == null) return;

        latestWaitingApplication.setIsAccepted(true);

        applicationRepository.save(latestWaitingApplication);
    }
}
