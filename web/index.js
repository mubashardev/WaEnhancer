// Register GSAP ScrollTrigger
gsap.registerPlugin(ScrollTrigger);

// Hero Animations
const heroTl = gsap.timeline();

heroTl.from(".hero h1", {
    y: 100,
    opacity: 0,
    duration: 1.2,
    ease: "expo.out"
})
.from(".hero p", {
    y: 40,
    opacity: 0,
    duration: 1,
    ease: "power3.out"
}, "-=0.8")
.from(".hero-btns", {
    y: 20,
    opacity: 0,
    duration: 1,
    ease: "power3.out"
}, "-=0.6");

// Parallax effect for blobs
gsap.to(".blob-1", {
    scrollTrigger: {
        trigger: "body",
        start: "top top",
        end: "bottom bottom",
        scrub: 2
    },
    y: 200,
    x: 100
});

gsap.to(".blob-2", {
    scrollTrigger: {
        trigger: "body",
        start: "top top",
        end: "bottom bottom",
        scrub: 3
    },
    y: -300,
    x: -150
});

// Reveal Feature Cards and List Items
const featureCards = document.querySelectorAll(".reveal");

featureCards.forEach((card) => {
    const listItems = card.querySelectorAll("li");
    
    const cardTl = gsap.timeline({
        scrollTrigger: {
            trigger: card,
            start: "top 85%",
            toggleActions: "play none none none"
        }
    });

    cardTl.from(card, {
        y: 60,
        opacity: 0,
        duration: 1,
        ease: "power4.out"
    })
    .from(listItems, {
        opacity: 0,
        x: -20,
        stagger: 0.1,
        duration: 0.5,
        ease: "power2.out"
    }, "-=0.5");
});

// Header scroll effect
ScrollTrigger.create({
    start: "top -80",
    onUpdate: (self) => {
        const header = document.querySelector("header");
        if (self.direction === 1) { // Scrolling down
            header.style.padding = "0.75rem 5%";
            header.style.background = "rgba(2, 6, 23, 0.9)";
        } else { // Scrolling up
            header.style.padding = "1rem 5%";
            header.style.background = "rgba(2, 6, 23, 0.7)";
        }
    }
});

// Smooth Scroll for Navigation
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        const targetId = this.getAttribute('href');
        if (targetId === '#') return;
        
        e.preventDefault();
        const targetElement = document.querySelector(targetId);
        if (targetElement) {
            targetElement.scrollIntoView({
                behavior: 'smooth'
            });
        }
    });
});
