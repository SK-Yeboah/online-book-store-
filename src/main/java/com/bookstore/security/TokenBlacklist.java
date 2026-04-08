package com.bookstore.security;

import java.time.Duration;

public interface TokenBlacklist {

    void blacklist(String jti, Duration remainingTtl);
    boolean isBlackListed(String jti);

    
}
//  TokenBlacklist {

//     private final Set<String> blackListedJtis = ConcurrentHashMap.newKeySet();

//     public void add(String jti){
//         blackListedJtis.add(jti);
//     }

//     public boolean isBlackListed(String jti){
//         return jti != null && blackListedJtis.contains(jti);
//     }
    
// }
