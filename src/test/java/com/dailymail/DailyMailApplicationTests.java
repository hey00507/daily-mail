package com.dailymail;

import org.junit.jupiter.api.Test;

class DailyMailApplicationTests {

	@Test
	void main_클래스_존재() {
		// Spring context 로드는 환경변수(API key, Gmail 등)가 필요하므로
		// 단위 테스트에서는 클래스 존재만 확인
		DailyMailApplication app = new DailyMailApplication();
	}
}
