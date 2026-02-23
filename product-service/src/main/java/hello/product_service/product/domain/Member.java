package hello.product_service.product.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@NoArgsConstructor
@Getter
public class Member {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private int age;



    public static Member create(String name, int age) {
        Member member = new Member();
        member.age = age;
        member.name = name;

        return member;
    }
}
